package reka.process;

import static reka.util.Util.createEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MultiProcessManager implements ProcessManager {

	private final BlockingDeque<Entry<String,Consumer<String>>> q = new LinkedBlockingDeque<>();
	private final ProcessBuilder builder;
	private final Collection<SimpleProcessManager> all = new ArrayList<>();
	
	public MultiProcessManager(ProcessBuilder builder, int count, boolean noreply, AtomicReference<Consumer<String>> trigger) {
		this.builder = builder;
		
		for (int i = 0; i < count; i++) {
			all.add(new SimpleProcessManager(this.builder, q, noreply, trigger));
		}
		
		/*
		this.manager = ThreadLocal.withInitial(() -> {
			SimpleProcessManager m = new SimpleProcessManager(this.builder, q);
			all.add(m);
			return m;
		});
		*/ 
	}
	
	@Override
	public void run(String input, Consumer<String> consumer) {
		//manager.get().run(input, consumer);
		q.offer(createEntry(input, consumer));
	}


	@Override
	public void run(String input) {
		run(input, null);
	}

	@Override
	public void kill() {
		all.forEach(m -> m.kill());
	}
	
}