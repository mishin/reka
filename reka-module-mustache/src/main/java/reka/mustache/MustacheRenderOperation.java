package reka.mustache;

import static reka.data.content.Contents.utf8;

import java.io.StringReader;
import java.io.StringWriter;

import reka.data.MutableData;
import reka.data.content.Content;
import reka.flow.ops.Operation;
import reka.flow.ops.OperationContext;
import reka.util.Path;
import reka.util.Path.Response;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

public class MustacheRenderOperation implements Operation {
	
	private final static MustacheFactory mf = new DefaultMustacheFactory();
	
	private final Mustache mustache;
	private final Path in, out;
	private final boolean isMain;
	private final Content TEXT_HTML = utf8("text/html");
	
	public MustacheRenderOperation(String template, Path inputPath, Path outputPath) {
		mustache = mf.compile(new StringReader(template), "template");
		this.in = inputPath;
		this.out = outputPath;
		isMain = out.equals(Response.CONTENT);
	}

	@Override
	public void call(MutableData data, OperationContext ctx) {
		StringWriter writer = new StringWriter();
		mustache.execute(writer, data.at(in).viewAsMap());
		data.putString(out, writer.toString());
		if (isMain) data.put(Response.Headers.CONTENT_TYPE, TEXT_HTML);
	}

}
