package reka.clojure;

import static reka.api.Path.slashes;
import reka.core.bundle.RekaBundle;

public class ClojureBundle implements RekaBundle {

	public void setup(BundleSetup setup) {
		setup.use(slashes("clojure/embedded"), () -> new UseClojure());
	}
}