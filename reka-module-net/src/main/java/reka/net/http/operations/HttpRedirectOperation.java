package reka.net.http.operations;

import static reka.api.content.Contents.integer;
import static reka.api.content.Contents.utf8;

import java.util.function.Function;

import reka.api.Path.Response;
import reka.api.content.Content;
import reka.api.data.Data;
import reka.api.data.MutableData;
import reka.api.run.Operation;
import reka.api.run.OperationContext;

public class HttpRedirectOperation implements Operation {

	private final Function<Data,String> urlFn;
	private final Content status;
	private final Content content = utf8("");
	
	public HttpRedirectOperation(Function<Data,String> urlFn, boolean temporary) {
		this.urlFn = urlFn;
		status = integer(temporary ? 302 : 301);
	}
	
	@Override
	public void call(MutableData data, OperationContext ctx) {
		data.put(Response.STATUS, status)
			.put(Response.CONTENT, content)
			.putString(Response.Headers.LOCATION, urlFn.apply(data));
	}

}