package reka.core.builder;

import reka.api.data.Data;
import reka.api.data.MutableData;
import reka.api.flow.Flow;
import reka.api.run.Subscriber;
import reka.core.runtime.FlowContext;
import reka.core.runtime.handlers.ActionHandler;
import reka.core.runtime.handlers.ErrorHandler;
import reka.core.runtime.handlers.HaltedHandler;

public class EmbeddedFlowAction implements ActionHandler {

	private final Flow flow;
	private final ActionHandler next;
	private final HaltedHandler halted;
	private final ErrorHandler error;
	
	public EmbeddedFlowAction(Flow flow, ActionHandler next, HaltedHandler halted, ErrorHandler error) {
		this.flow = flow;
		this.next = next;
		this.halted = halted;
		this.error = error;
	}
	
	@Override
	public void call(MutableData data, FlowContext context) {
		flow.run(context.executor(), data, new Subscriber() {
				
			@Override
			public void ok(MutableData data) {
				context.call(next, error, data);
			}
			
			@Override
			public void halted() {
				halted.halted(context);
			}
			
			@Override
			public void error(Data data, Throwable t) {
				error.error(data, context, t);
			}
			
		}, context.statsEnabled());	
	}

}
