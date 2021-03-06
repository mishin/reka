package reka.nashorn;

import static reka.nashorn.NashornConfigurer.RUNNER;
import static reka.util.Path.dots;
import reka.config.Config;
import reka.config.configurer.annotations.Conf;
import reka.module.setup.OperationConfigurer;
import reka.module.setup.OperationSetup;
import reka.util.Path;

public class NashornRunConfigurer implements OperationConfigurer {
	
	private String script;
	private Path out;
	
	public NashornRunConfigurer(Path defaultWriteTo) {
		this.out = defaultWriteTo;
	}

	@Conf.Val
	@Conf.At("into")
	public void out(String val) {
		out = dots(val);
	}
	
	@Conf.Config
	@Conf.At("script")
	public void config(Config config) {
		if (config.hasDocument()) {
			script = config.documentContentAsString();
			if (config.hasValue()) {
				out = dots(config.valueAsString());
			}
		} else if (config.hasValue()) {
			script = config.valueAsString();
		}
	}

	@Override
	public void setup(OperationSetup ops) {
		ops.add("run", () -> new NashornRunOperation(ops.ctx().get(RUNNER), script, out));
	}

}
