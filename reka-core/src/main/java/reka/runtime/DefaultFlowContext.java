package reka.runtime;

import static reka.util.Util.unchecked;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import reka.data.Data;
import reka.data.MutableData;
import reka.flow.Flow.FlowStats;
import reka.flow.ops.Subscriber;
import reka.identity.IdentityStoreReader;
import reka.runtime.handlers.ActionHandler;
import reka.runtime.handlers.ErrorHandler;
import reka.runtime.handlers.HaltedHandler;
import reka.runtime.handlers.stateful.DefaultNodeState;
import reka.runtime.handlers.stateful.NodeState;

public class DefaultFlowContext implements FlowContext {

	private static final AtomicLong contextIds = new AtomicLong();
	
	private final long contextId = contextIds.incrementAndGet();
	
	public static FlowContext create(long flowId, ExecutorService operationExecutor, ExecutorService coordinationExecutor, 
			                         Subscriber subscriber, IdentityStoreReader store, FlowStats stats) {
		return new DefaultFlowContext(flowId, operationExecutor, coordinationExecutor, subscriber, store, stats);
	}

	private final FlowStats stats;
	private final ExecutorService operationExecutor;
	private final ExecutorService coordinationExecutor;
	private final Map<Integer, NodeState> states = new HashMap<>();
	private final IdentityStoreReader store;
	private final Subscriber subscriber;
	private final long flowId;
	private final long started;

	private volatile boolean done = false;

	private final boolean statsEnabled;

	private volatile long threadId = -1; // only used when asserts are on

	private DefaultFlowContext(long flowId, ExecutorService operationExecutor,
			ExecutorService coordinationExecutor, Subscriber subscriber,
			IdentityStoreReader store, FlowStats stats) {
		this.operationExecutor = operationExecutor;
		this.coordinationExecutor = coordinationExecutor;
		this.subscriber = subscriber;
		this.flowId = flowId;
		this.stats = stats;
		this.store = store;
		this.statsEnabled = stats != null;
		started = System.nanoTime();
		if (statsEnabled) stats.requests.increment();
		assert calculateThreadId();

	}

	@Override
	public long flowId() {
		return flowId;
	}
	
	@Override
	public long contextId() {
		return contextId;
	}

	@Override
	public long started() {
		return started;
	}

	@Override
	public ExecutorService operationExecutor() {
		return operationExecutor;
	}

	@Override
	public ExecutorService coordinationExecutor() {
		return coordinationExecutor;
	}

	@Override
	public void handleAction(ActionHandler next, ErrorHandler error, MutableData data) {
		coordinationExecutor.execute(() -> {
			assert !done : "stop calling me, we're done!";
			try {
				next.call(data, this);
			} catch (Throwable t) {
				error.error(data, this, t);
			}
		});
	}


	@Override
	public void handleHalted(HaltedHandler halted) {
		coordinationExecutor.execute(() -> {
			halted.halted(this);
		});
	}

	@Override
	public void handleError(ErrorHandler error, Data data, Throwable t) {
		coordinationExecutor.execute(() -> {
			error.error(data, this, t);
		});
	}
	
	@Override
	public NodeState stateFor(int id) {
		assert hasCorrectThread() : "wrong thread " + Thread.currentThread().getId() + " vs " + threadId;
		NodeState state = states.get(id);
		if (state == null) {
			state = DefaultNodeState.get();
			states.put(id, state);
		}
		return state;
	}

	@Override
	public void end(MutableData data) {
		assert hasCorrectThread() : "wrong thread " + Thread.currentThread().getId() + " vs " + threadId;
		done = true;
		operationExecutor.execute(() -> {
			subscriber.ok(data);
		});
		if (statsEnabled) stats.completed.increment();
	}

	@Override
	public void error(Data data, Throwable t) {
		assert hasCorrectThread() : "wrong thread " + Thread.currentThread().getId() + " vs " + threadId;
		done = true;
		operationExecutor.execute(() -> {
			subscriber.error(data, t);
		});
		if (statsEnabled) stats.errors.increment();
	}

	@Override
	public void halted() {
		assert hasCorrectThread();
		done = true;
		operationExecutor.execute(() -> {
			subscriber.halted();
		});
		if (statsEnabled) stats.halts.increment();
	}

	@Override
	public boolean statsEnabled() {
		return statsEnabled;
	}

	@Override
	public IdentityStoreReader store() {
		return store;
	}

	private boolean hasCorrectThread() {
		return Thread.currentThread().getId() == threadId;
	}

	private boolean calculateThreadId() {
		CountDownLatch latch = new CountDownLatch(1);
		coordinationExecutor.execute(() -> {
			threadId = Thread.currentThread().getId();
			latch.countDown();
		});
		try {
			if (Thread.currentThread().getId() != threadId)	latch.await();
		} catch (InterruptedException e) {
			throw unchecked(e);
		}
		return true;
	}

}