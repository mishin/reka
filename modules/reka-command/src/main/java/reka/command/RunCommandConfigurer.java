package reka.command;

import java.util.List;

import reka.core.setup.OperationConfigurer;
import reka.core.setup.OperationSetup;

public class RunCommandConfigurer implements OperationConfigurer {
	
	private final String exec;
	private final List<String> args;
	
	public RunCommandConfigurer(String exec, List<String> args) {
		this.exec = exec;
		this.args = args;
	}

	@Override
	public void setup(OperationSetup ops) {
		ops.add("run", store -> new RunCommandOperation(exec, args));
	}

}