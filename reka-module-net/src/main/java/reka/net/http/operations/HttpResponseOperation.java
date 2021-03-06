package reka.net.http.operations;

import static reka.data.content.Contents.binary;
import static reka.data.content.Contents.integer;
import static reka.data.content.Contents.utf8;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;

import reka.data.MutableData;
import reka.data.content.Content;
import reka.flow.ops.Operation;
import reka.flow.ops.OperationContext;
import reka.util.Path.Response;

import com.google.common.collect.ImmutableMap;

public class HttpResponseOperation implements Operation {
	
	private final Content content;
	private final Content contentType;
	private final Content status;
	private final Map<String,Content> headers;
	
	public HttpResponseOperation(String content, String contentType, int status, Map<String,String> headers) {
		byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
		ByteBuffer bb = ByteBuffer.allocateDirect(contentBytes.length).put(contentBytes);
		bb.flip();
		this.content = binary(contentType, bb);
		this.contentType = utf8(contentType);
		this.status = integer(status);
		ImmutableMap.Builder<String,Content> mapBuilder = ImmutableMap.builder();
		for (Entry<String,String> header : headers.entrySet()) {
			mapBuilder.put(header.getKey(), utf8(header.getValue()));
		}
		this.headers = mapBuilder.build();
	}


	@Override
	public void call(MutableData data, OperationContext ctx) {
		
		for (Entry<String, Content> header : headers.entrySet()) {
			data.put(Response.HEADERS.add(header.getKey()), header.getValue());
		}
		
		data.put(Response.CONTENT, content)
			.put(Response.Headers.CONTENT_TYPE, contentType)
			.put(Response.STATUS, status)
		;
	}
	
}
