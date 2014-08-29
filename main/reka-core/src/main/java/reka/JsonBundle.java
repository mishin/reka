package reka;

import static reka.api.Path.dots;
import static reka.api.Path.path;
import static reka.core.builder.FlowSegments.sync;
import static reka.util.Util.unchecked;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.codehaus.jackson.map.ObjectMapper;

import reka.api.Path;
import reka.api.data.Data;
import reka.api.data.MutableData;
import reka.api.flow.FlowSegment;
import reka.api.run.SyncOperation;
import reka.config.configurer.Configurer.ErrorCollector;
import reka.config.configurer.ErrorReporter;
import reka.config.configurer.annotations.Conf;
import reka.core.bundle.ModuleConfigurer;
import reka.core.bundle.ModuleSetup;
import reka.core.bundle.RekaBundle;
import reka.core.data.memory.MutableMemoryData;
import reka.core.util.StringWithVars;

public class JsonBundle implements RekaBundle {
	
	private static final ObjectMapper jsonMapper = new ObjectMapper();

	@Override
	public void setup(BundleSetup bundle) {
		bundle.module(path("json"), () -> new UseJson());
	}
	
	public static class UseJson extends ModuleConfigurer {

		@Override
		public void setup(ModuleSetup module) {
			module.operation(path("parse"), () -> new JsonParseConfigurer());
			module.operation(path("stringify"), () -> new JsonStringifyConfigurer());
		}
		
	}

	public static class JsonParseConfigurer implements Supplier<FlowSegment>, ErrorReporter {
		
		private Function<Data,Path> inFn, outFn;
		
		@Conf.Val
		@Conf.At("in")
		public void in(String val) {
			inFn = StringWithVars.compile(val).andThen(s -> dots(s));
			if (outFn == null) outFn = inFn;
		}

		@Conf.At("out")
		public void out(String val) {
			outFn = StringWithVars.compile(val).andThen(s -> dots(s));
		}

		@Override
		public void errors(ErrorCollector errors) {
			errors.checkConfigPresent(inFn, "in is required");
			errors.checkConfigPresent(outFn, "out is required");
		}

		@Override
		public FlowSegment get() {
			return sync("json/parse", () -> new JsonParseOperation(inFn, outFn));
		}
		
	}
	
	public static class JsonStringifyConfigurer implements Supplier<FlowSegment>, ErrorReporter {
		
		private Function<Data,Path> inFn, outFn;
		
		@Conf.Val
		@Conf.At("in")
		public void in(String val) {
			inFn = StringWithVars.compile(val).andThen(s -> dots(s));
			if (outFn == null) outFn = inFn;
		}

		@Conf.At("out")
		public void out(String val) {
			outFn = StringWithVars.compile(val).andThen(s -> dots(s));
		}

		@Override
		public void errors(ErrorCollector errors) {
			errors.checkConfigPresent(inFn, "in is required");
			errors.checkConfigPresent(outFn, "out is required");
		}

		@Override
		public FlowSegment get() {
			return sync("json/stringify", () -> new JsonStringifyOperation(inFn, outFn));
		}
		
	}
	
	public static class JsonParseOperation implements SyncOperation {
		
		private final Function<Data,Path> inFn, outFn;
		
		public JsonParseOperation(Function<Data,Path> inFn, Function<Data,Path> outFn) {
			this.inFn = inFn;
			this.outFn = outFn;
		}

		@Override
		public MutableData call(MutableData data) {
			Data val = data.at(inFn.apply(data));
			if (val.isContent()) {
				try {
					@SuppressWarnings("unchecked")
					Map<String,Object> map = jsonMapper.readValue(val.content().asUTF8(), Map.class);
					data.put(outFn.apply(data), MutableMemoryData.createFromMap(map));
				} catch (IOException e) {
					throw unchecked(e);
				}
			}
			return data;
		}
		
	}
	
	public static class JsonStringifyOperation implements SyncOperation {
		
		private final Function<Data,Path> inFn, outFn;
		
		public JsonStringifyOperation(Function<Data,Path> inFn, Function<Data,Path> outFn) {
			this.inFn = inFn;
			this.outFn = outFn;
		}

		@Override
		public MutableData call(MutableData data) {
			data.putString(outFn.apply(data), data.at(inFn.apply(data)).toJson());
			return data;
		}
		
	}
	
}
