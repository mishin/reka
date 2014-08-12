package reka.http;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static reka.api.Path.path;
import static reka.config.configurer.Configurer.configure;
import static reka.config.configurer.Configurer.Preconditions.checkConfig;
import static reka.util.Util.runtime;
import static reka.util.Util.unchecked;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import reka.DeployedResource;
import reka.api.flow.FlowSegment;
import reka.config.Config;
import reka.config.configurer.annotations.Conf;
import reka.core.bundle.UseConfigurer;
import reka.core.bundle.UseInit;
import reka.core.config.ConfigurerProvider;
import reka.core.config.SequenceConfigurer;
import reka.core.data.memory.MutableMemoryData;
import reka.http.configurers.HttpContentConfigurer;
import reka.http.configurers.HttpRedirectConfigurer;
import reka.http.configurers.HttpRequestConfigurer;
import reka.http.configurers.HttpRouterConfigurer;
import reka.http.server.HttpServerManager;
import reka.http.server.HttpSettings;
import reka.http.server.HttpSettings.SslSettings;
import reka.http.server.HttpSettings.Type;

public class UseHTTP extends UseConfigurer {

	// listen 8080
	// listen localhost:500
	// listen boo.com
	
	private final Pattern listenPortOnly = Pattern.compile("^[0-9]+$");
	private final Pattern listenHostAndPort = Pattern.compile("^(.+):([0-9]+)$");
	
	public class HostAndPort {
		private final String host;
		private final int port;
		public HostAndPort(String host, int port) {
			this.host = host;
			this.port = port;
		}
		public String host() {
			return host;
		}
		public int port() {
			return port;
		}
	}

	private final HttpServerManager server;
	
	private final List<HostAndPort> listens = new ArrayList<>();
	private final List<Function<ConfigurerProvider,Supplier<FlowSegment>>> requestHandlers = new ArrayList<>();
	
	private SslSettings sslSettings;
	
	@Conf.At("ssl")
	public void ssl(Config config) {
		sslSettings = configure(new SslConfigurer(), config).build();
	}
	
	public UseHTTP(HttpServerManager server) {
		this.server = server;
	}
	
	@Conf.Each("listen")
	public void listen(String val) {
		String host = null;
		int port = -1;
		if (listenPortOnly.matcher(val).matches()) {
			port = Integer.valueOf(val);
		} else {
			Matcher m = listenHostAndPort.matcher(val);
			if (m.matches()) {
				host = m.group(1);
				port = Integer.valueOf(m.group(2));
			} else {
				host = val;
			}
		}
		listens.add(new HostAndPort(host, port));
	}

	@Conf.Each("on")
	public void on(Config config) {
		checkConfig(config.hasValue(), "must have a value");
		switch (config.valueAsString()) {
		case "request":
			requestHandlers.add((provider) -> configure(new SequenceConfigurer(provider), config.body()));
			break;
		default:
			throw runtime("unknown trigger %s", config.valueAsString());
		}
	}

	@Override
	public void setup(UseInit http) {
		
		http.operation(path("router"), (provider) -> new HttpRouterConfigurer(provider));
		http.operation(path("redirect"), () -> new HttpRedirectConfigurer());
		http.operation(path("content"), () -> new HttpContentConfigurer());
		http.operation(asList(path("request"), path("req")), () -> new HttpRequestConfigurer(server.group()));
		
		for (Function<ConfigurerProvider, Supplier<FlowSegment>> h : requestHandlers) {
			
			http.trigger("request", h, register -> {
				
				for (HostAndPort listen : listens) {
					
					final String host = listen.host() == null ? "*" : listen.host();
					final int port = listen.port() == -1 ? (sslSettings != null ? 443 : 80) : listen.port();
					
					String identity = format("%s/%s/%s", register.identity(), host, port);
				
					HttpSettings settings;
					
					if (sslSettings != null) {
						settings = HttpSettings.https(port, host, Type.HTTP, sslSettings, register.applicationVersion());
					} else {
						settings = HttpSettings.http(port, host, Type.HTTP, register.applicationVersion());
					}
					
					server.deployHttp(identity, register.flow(), settings);
					
					register.resource(new DeployedResource() {
						
						@Override
						public void undeploy(int version) {
							server.undeploy(identity, version);	
						}
						
						@Override
						public void pause(int version) {
							server.pause(identity, version);
						}

						
						@Override
						public void resume(int version) {
							server.resume(identity, version);
						}
						
					});
					
					register.network(port, settings.isSsl() ? "https" : "http", MutableMemoryData.create((details) -> {
						details.putString("host", host);
						details.putString("run", register.flow().name().last().toString());
					}).immutable());
				
				}
			});
		}
		
	}
	
	public static class SslConfigurer {
		
		private byte[] crt;
		private byte[] key;
		
		@Conf.At("crt")
		public void crt(Config val) {
			checkConfig(val.hasDocument(), "must have document!");
			crt = val.documentContent();
		}
		
		@Conf.At("key")
		public void key(Config val) {
			checkConfig(val.hasDocument(), "must have document!");
			key = val.documentContent();
		}
		
		SslSettings build() {
			return new SslSettings(byteToFile(crt), byteToFile(key));
		}
		
		private static File byteToFile(byte[] bytes) {
			try {
				java.nio.file.Path tmp = Files.createTempFile("reka.", "");
				Files.write(tmp, bytes);
				return tmp.toFile();
			} catch (IOException e) {
				throw unchecked(e);
			}
		}
		
	}

}