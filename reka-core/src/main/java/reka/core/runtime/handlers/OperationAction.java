package reka.core.runtime.handlers;

import reka.api.data.MutableData;
import reka.api.run.Operation;
import reka.api.run.OperationContext;
import reka.core.runtime.FlowContext;

public class OperationAction implements ActionHandler {

	private final Operation operation;
	private final ActionHandler next;
	private final ErrorHandler error;
	
	public OperationAction(Operation operation, ActionHandler next, ErrorHandler error) {
		this.operation = operation;
		this.next = next;
		this.error = error;
	}
	
	@Override
	public void call(MutableData data, FlowContext context) {
		context.operationExecutor().execute(() -> {
			try {
				// TODO: don't create a new context each time
				operation.call(data, new OperationContext(context.store()));
				context.handleAction(next, error, data);
			} catch (Throwable t) {
				context.handleError(error, data, t);
			}
		});
	}

}