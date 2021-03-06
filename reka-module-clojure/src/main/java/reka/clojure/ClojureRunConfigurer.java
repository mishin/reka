package reka.clojure;

import static reka.clojure.ClojureConfigurer.CLOJURE_ENV;
import reka.config.configurer.annotations.Conf;
import reka.module.setup.OperationConfigurer;
import reka.module.setup.OperationSetup;

public class ClojureRunConfigurer implements OperationConfigurer {

	private String fn;
	
	@Conf.Val
	public void fn(String val) {
		fn = val;
	}
	
	@Override
	public void setup(OperationSetup ops) {
		ops.add("run", () -> new ClojureRunOperation(ops.ctx().get(CLOJURE_ENV), fn));
	}
	
}