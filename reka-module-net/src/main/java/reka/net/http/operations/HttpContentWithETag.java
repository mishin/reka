package reka.net.http.operations;

import static reka.api.content.Contents.integer;
import static reka.api.content.Contents.utf8;
import static reka.util.Util.hex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import reka.api.Path.Request;
import reka.api.Path.Response;
import reka.api.content.Content;
import reka.api.content.Contents;
import reka.api.data.MutableData;
import reka.api.run.Operation;
import reka.api.run.OperationContext;
import reka.net.http.operations.HttpContentUtils.ContentAndType;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

public class HttpContentWithETag implements Operation {
	
	private static final HashFunction sha1 =  Hashing.sha1();
	
	private final Content content, contentType;
	
	private final String etagValue;
	private final Content etag;
	
	private static final Content EMPTY = Contents.nullValue();	
	private static final Content NOT_MODIFIED = integer(304);
	
	protected HttpContentWithETag(Path tmpdir, Content content, String contentType) {
		ContentAndType vals = HttpContentUtils.convert(tmpdir, content, contentType);
		this.content = vals.content();
		this.contentType = vals.type();
		Hasher hasher = sha1.newHasher();
		content.hash(hasher);
		hasher.putString(contentType, StandardCharsets.UTF_8);
		etagValue = hex(hasher.hash().asBytes());
		etag = utf8(etagValue);
	}

	@Override
	public void call(MutableData data, OperationContext ctx) {
		if (data.existsAt(Request.Headers.IF_NONE_MATCH) && etagValue.equals(data.getString(Request.Headers.IF_NONE_MATCH).orElse(""))) {
			data.put(Response.CONTENT, EMPTY)
				.put(Response.STATUS, NOT_MODIFIED);
		} else {
			data.put(Response.CONTENT, content)
				.put(Response.Headers.CONTENT_TYPE, contentType)
				.put(Response.Headers.ETAG, etag);
		}
	}

}