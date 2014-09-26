package reka.common.bcrypt;

import static reka.api.Path.dots;
import reka.api.Path;
import reka.config.configurer.annotations.Conf;
import reka.core.setup.OperationConfigurer;
import reka.core.setup.OperationSetup;

public class BCryptHashpwConfigurer implements OperationConfigurer {

	private Path in = dots("bcrypt.pw");
	private Path out = dots("bcrypt.hash");
	
	@Conf.At("in")
	public void in(String val) {
		in = dots(val);
	}
	
	@Conf.At("out")
	public void out(String val) {
		out = dots(val);
	}
	
	@Override
	public void setup(OperationSetup ops) {
		ops.add("bcrypt/hashpw", store -> new BCryptHashpwOperation(in, out));
	}
	
}