package reka.http.configurers;

import static reka.api.content.Contents.binary;
import static reka.api.content.Contents.utf8;
import static reka.config.configurer.Configurer.Preconditions.checkConfig;
import reka.api.content.Content;
import reka.api.content.types.BinaryContent;
import reka.api.data.Data;
import reka.config.Config;
import reka.config.configurer.annotations.Conf;
import reka.core.setup.OperationConfigurer;
import reka.core.setup.OperationSetup;
import reka.http.operations.HttpContentUtils;

public class HttpContentConfigurer implements OperationConfigurer {
	
	private Content content;
	private String contentType;
	
	@Conf.Config
	public void config(Config config) {
		if (config.hasDocument()) {
			checkConfig(config.documentType() != null, "no document type!");
			content = binary(config.documentType(), config.documentContent());
			if (config.documentType() != null) {
				contentType = config.documentType();
			}
		} else if (config.hasValue()) {
			content = utf8(config.valueAsString());
			contentType = "text/plain";
		}
	}
	
	@Conf.At("content")
	public HttpContentConfigurer content(String value) {
		content = utf8(value);
		return this;
	}

	public HttpContentConfigurer content(Data value) {
		content = utf8(value.toJson());
		contentType = "application/json";
		return this;
	}

	@Conf.At("content-type")
	public HttpContentConfigurer contentType(String value) {
		contentType = value;
		return this;
	}
	
	@Override
	public void setup(OperationSetup ops) {
		if (content instanceof BinaryContent && contentType == null) {
			String ct = ((BinaryContent) content).contentType();
			if (ct != null) {
				contentType = ct;
			}
		}
		ops.add("content", store -> HttpContentUtils.httpContent(content, contentType, true));
	}

}