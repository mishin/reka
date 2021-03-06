package reka.jsx;
import static reka.config.configurer.Configurer.Preconditions.checkConfig;
import static reka.data.content.Contents.utf8;
import static reka.util.Path.root;
import static reka.util.Util.hex;
import static reka.util.Util.runtime;
import static reka.util.Util.unchecked;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reka.config.Config;
import reka.config.configurer.annotations.Conf;
import reka.data.Data;
import reka.data.MutableData;
import reka.data.content.Content;
import reka.flow.ops.Operation;
import reka.flow.ops.OperationContext;
import reka.identity.IdentityKey;
import reka.module.setup.AppSetup;
import reka.module.setup.ModuleConfigurer;
import reka.module.setup.ModuleSetupContext;
import reka.module.setup.OperationConfigurer;
import reka.module.setup.OperationSetup;
import reka.util.Path;
import reka.util.Path.Response;


public class JsxConfigurer extends ModuleConfigurer {
	
	@SuppressWarnings("unused")
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	protected static final IdentityKey<String> COMPILED = IdentityKey.named("compiled jsx");
	
	private final StringBuilder src = new StringBuilder();
	
	@Conf.Each("template")
	public void template(Config config) {
		checkConfig(config.hasDocument(), "template must have document");
		src.append(config.documentContentAsString()).append("\n\n");
	}
	
	@Override
	public void setup(AppSetup app) {
		
		ModuleSetupContext ctx = app.ctx();
		
		app.onDeploy(init -> {
			init.run("compile jsx", () -> {
				String jsx = src.toString();
				try {
					MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
					sha1.reset();
					sha1.update(jsx.getBytes(StandardCharsets.UTF_8));
					String hex = hex(sha1.digest());
					
					String compiled;
					
					File cacheFile = dirs().tmp().resolve("jsx." + hex).toFile();
					if (cacheFile.exists()) {
						compiled = new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
					} else {
						Map<String,Object> data = new HashMap<>();
						data.put("src", jsx);
						Map<String,Object> map = new HashMap<>();
						map.put("data", data);
						Object result = JsxModule.runner().run(JsxModule.jsxCompiler(), map);
						if (!(result instanceof String)) throw runtime("compiler.js must return a String");
						compiled = (String) result;
						Files.write(cacheFile.toPath(), compiled.getBytes(StandardCharsets.UTF_8));
					}
					ctx.put(COMPILED, compiled);
				} catch (Throwable t) {
					t.printStackTrace();
					throw unchecked(t);
				}
			});
		});
		
		app.defineOperation(root(), provider -> new JsxTemplateConfigurer());
	}
	
	public static class JsxTemplateConfigurer implements OperationConfigurer {
		
		private final Function<Data,Path> outFn = data -> Response.CONTENT;

		@Override
		public void setup(OperationSetup ops) {
			ops.add("template", () -> new JsxTemplateOperation(ops.ctx().get(COMPILED), outFn));
		}
		
	}
	
	public static class JsxTemplateOperation implements Operation {
		
		private static final Content APPLICATION_JAVASCRIPT = utf8("application/javascript");
		
		private final Content content;
		private final Function<Data,Path> outFn;
		
		public JsxTemplateOperation(String content, Function<Data,Path> outFn) {
			this.content = utf8(content);
			this.outFn = outFn;
		}

		@Override
		public void call(MutableData data, OperationContext ctx) {
			Path out = outFn.apply(data);
			data.put(out, content);
			if (out.equals(Response.CONTENT)) {
				data.put(Response.Headers.CONTENT_TYPE, APPLICATION_JAVASCRIPT);
			}
		}
		
	}

}
