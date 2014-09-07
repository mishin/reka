package reka.http.configurers;

import java.util.function.Function;

import reka.api.data.Data;
import reka.config.configurer.annotations.Conf;
import reka.core.bundle.OperationSetup;
import reka.core.util.StringWithVars;
import reka.http.operations.HttpRedirectOperation;
import reka.nashorn.OperationsConfigurer;

public class HttpRedirectConfigurer implements OperationsConfigurer {

	private Function<Data,String> urlFn;
	private boolean temporary = true;
	
	@Conf.Val
	@Conf.At("url")
	public void url(String val) {
	    urlFn = StringWithVars.compile(val);
	}
	
	@Override
	public void setup(OperationSetup ops) {
		ops.add("http/redirect", store -> new HttpRedirectOperation(urlFn, temporary));
	}

}
