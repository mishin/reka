package reka;

import reka.core.setup.ModuleConfigurer;
import reka.core.setup.ModuleSetup;

public class RootModule extends ModuleConfigurer {
	
	public RootModule() {
		name("root");
		isRoot(true);
	}

	@Override
	public void setup(ModuleSetup module) { }

}
