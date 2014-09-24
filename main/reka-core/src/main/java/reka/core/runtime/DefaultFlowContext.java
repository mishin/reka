package reka.core.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import reka.api.data.Data;
import reka.api.data.MutableData;
import reka.api.flow.Flow.FlowStats;
import reka.api.run.Subscriber;
import reka.core.runtime.handlers.ActionHandler;
import reka.core.runtime.handlers.ErrorHandler;
import reka.core.runtime.handlers.stateful.DefaultNodeState;
import reka.core.runtime.handlers.stateful.NodeState;

public class DefaultFlowContext implements FlowContext {
	
	public static FlowContext create(long flowId, ExecutorService executor, Subscriber subscriber, FlowStats stats) {
		return new DefaultFlowContext(flowId, executor, subscriber, stats);
	}

	private final FlowStats stats;
	private final ExecutorService executor;
	private final Map<Integer,NodeState> states = new HashMap<>();
	private final Subscriber subscriber;
	private final long flowId;
	private final long started;
	
	private volatile boolean done = false;
	
	private final boolean statsEnabled;
	
	private DefaultFlowContext(long flowId, ExecutorService executor, Subscriber subscriber, FlowStats stats) {
		this.executor = executor;
		this.subscriber = subscriber;
		this.flowId = flowId;
		this.stats = stats;
		this.statsEnabled = stats != null;
		started = System.nanoTime();
		if (statsEnabled) stats.requests.increment();	
	}

	@Override
    public NodeState stateFor(int id) {
		NodeState state = states.get(id);
		if (state == null) {
			state = DefaultNodeState.get();
			states.put(id, state);
		}
		return state;
	}
	
	@Override
	public long flowId() {
		return flowId;
	}
	
	@Override
	public long started() {
		return started;
	}
	
	@Override
    public ExecutorService executor() {
		return executor;
	}

	@Override
	public void execute(Runnable runnable) {
		if (done) return;
		executor.execute(runnable);
	}

	@Override
	public void call(ActionHandler next, ErrorHandler error, MutableData data) {
		if (done) return;
		execute(() -> {
			try {
				next.call(data, this);
			} catch (Throwable t) {
				error.error(data, this, t);
			}
		});
	}

	@Override
	public void end(MutableData data) {
		done = true;
		subscriber.ok(data);
	}

	@Override
	public void error(Data data, Throwable t) {
		done = true;
		subscriber.error(data, t);
		if (statsEnabled) stats.errors.increment();
	}

	@Override
	public void halted() {
		done = true;
		subscriber.halted();
		if (statsEnabled) stats.halts.increment();
	}

	@Override
	public boolean statsEnabled() {
		return statsEnabled;
	}
	
}