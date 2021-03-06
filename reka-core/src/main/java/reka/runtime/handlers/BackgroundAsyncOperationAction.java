package reka.runtime.handlers;

import java.util.concurrent.ExecutorService;

import reka.data.MutableData;
import reka.flow.ops.AsyncOperation;
import reka.flow.ops.AsyncOperation.OperationResult;
import reka.flow.ops.OperationContext;
import reka.runtime.FlowContext;

public class BackgroundAsyncOperationAction implements ActionHandler {

	private final AsyncOperation op;
	private final ActionHandler next;
	private final ErrorHandler error;
	private final ExecutorService backgroundExecutor;
	
	public BackgroundAsyncOperationAction(AsyncOperation op, ActionHandler next, ErrorHandler error, ExecutorService backgroundExecutor) {
		this.op = op;
		this.next = next;
		this.error = error;
		this.backgroundExecutor = backgroundExecutor;
	}
	
	@Override
	public void call(MutableData data, FlowContext context) {
		backgroundExecutor.execute(() -> {
			try {
				// TODO: don't create new one each time
				op.call(data, new OperationContext(context.store()), new OperationResult(){
		
					@Override
					public void done() {
						context.handleAction(next, error, data);
					}
		
					@Override
					public void error(Throwable t) {
						context.handleError(error, data, t);
					}
					
				});
			} catch (Throwable t) {
				context.handleError(error, data, t);
			}
		});
	}

}
