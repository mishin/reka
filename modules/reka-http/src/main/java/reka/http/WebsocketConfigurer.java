package reka.http;

import static java.lang.String.format;
import static reka.api.Path.path;
import static reka.config.configurer.Configurer.configure;
import static reka.config.configurer.Configurer.Preconditions.checkConfig;
import static reka.util.Util.runtime;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import reka.api.IdentityKey;
import reka.api.Path;
import reka.api.data.Data;
import reka.api.data.MutableData;
import reka.api.flow.Flow;
import reka.api.run.Operation;
import reka.config.Config;
import reka.config.ConfigBody;
import reka.config.configurer.annotations.Conf;
import reka.core.config.ConfigurerProvider;
import reka.core.config.SequenceConfigurer;
import reka.core.setup.ModuleConfigurer;
import reka.core.setup.ModuleSetup;
import reka.core.setup.OperationConfigurer;
import reka.core.setup.OperationSetup;
import reka.core.setup.StatusDataProvider;
import reka.core.util.StringWithVars;
import reka.http.server.HttpServerManager;
import reka.http.server.HttpSettings;
import reka.http.server.HttpSettings.SslSettings;
import reka.http.server.HttpSettings.Type;

public class WebsocketConfigurer extends ModuleConfigurer {
	
	private final Pattern listenPortOnly = Pattern.compile("^[0-9]+$");
	private final Pattern listenHostAndPort = Pattern.compile("^(.+):([0-9]+)$");
	
	private final List<HostAndPort> listens = new ArrayList<>();
	
	private SslSettings ssl;
	
	private final HttpServerManager server;
	
	private static final IdentityKey<HttpSettings> HTTP_SETTINGS = IdentityKey.named("http settings");
	
	public WebsocketConfigurer(HttpServerManager server) {
		this.server = server;
	}
	
	private final List<ConfigBody> onConnect = new ArrayList<>();
	private final List<ConfigBody> onDisconnect = new ArrayList<>();
	private final List<ConfigBody> onMessage = new ArrayList<>();
	
	private final List<WebsocketTopicConfigurer> topics = new ArrayList<>();

	@Conf.Each("listen")
	public void listen(String val) {
		checkConfig(listens.isEmpty(), "can only add one listen for websockets");
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
	
	@Conf.At("ssl")
	public void ssl(Config config) {
		ssl = configure(new SslConfigurer(), config).build();
	}
	
	@Conf.Each("topic")
	public void topic(Config config) {
		topics.add(configure(new WebsocketTopicConfigurer(IdentityKey.named(config.valueAsString())), config.body()));
	}
	
	@Conf.Each("on")
	public void on(Config config) {
		checkConfig(config.hasValue(), "must have a value");
		checkConfig(config.hasBody(), "must have a body");
		switch (config.valueAsString()) {
		case "connect":
			onConnect.add(config.body());
			break;
		case "disconnect":
			onDisconnect.add(config.body());
			break;
		case "message":
			onMessage.add(config.body());
			break;
		default:
			throw runtime("unknown trigger %s", config.valueAsString());
		}
	}

	private static Function<ConfigurerProvider,OperationConfigurer> combine(List<ConfigBody> bodies) {
		return provider -> {
			return ops -> {
				ops.parallel(par -> {
					bodies.forEach(body -> {
						par.add(configure(new SequenceConfigurer(provider), body));
					});
				});
			};
		};
	}
	
	@Override
	public void setup(ModuleSetup module) {
		
		listens.replaceAll(listen -> listen.port() == -1 ? new HostAndPort(listen.host(), ssl != null ? 443 : 80) : listen);
		
		module.operation(path("send"), provider -> new WebsocketSendConfigurer());
		module.operation(path("broadcast"), provider -> new WebsocketBroadcastConfigurer());
		
		module.status(store -> new WebsocketStatusProvider(store.get(HTTP_SETTINGS)));
		
		topics.forEach(topic -> {
			Path base = path(topic.key().name());
			module.operation(base, provider -> new WebsocketTopicSendConfigurer(topic.key()));
			module.operation(base.add("subscribe"), provider -> new WebsocketTopicSubscribeConfigurer(topic.key()));
		});
		
		Map<IdentityKey<Flow>,Function<ConfigurerProvider, OperationConfigurer>> triggers = new HashMap<>();
		
		IdentityKey<Flow> connect = IdentityKey.named("connect");
		IdentityKey<Flow> disconnect = IdentityKey.named("disconnect");
		IdentityKey<Flow> message = IdentityKey.named("message");
		
		if (!onConnect.isEmpty()) {
			triggers.put(connect, combine(onConnect));
		}
		if (!onDisconnect.isEmpty()) {
			triggers.put(disconnect, combine(onDisconnect));
		}
		if (!onMessage.isEmpty()) {
			triggers.put(message, combine(onMessage));
		}
		
		if (listens.isEmpty()) return;
		
		module.setupInitializer(init -> {
			init.run("set http settings", store -> {
				// FIXME: hackety hack, don't look back, these aren't the real HTTP settings!
				int port = listens.get(0).port();
				if (port == -1) {
					port = ssl != null ? 443 : 80;
				}
				store.put(HTTP_SETTINGS, HttpSettings.http(port, listens.get(0).host(), Type.WEBSOCKET, null, -1));
			});
		});
		
		module.triggers(triggers, registration -> {
			
			for (HostAndPort listen : listens) {
			
				String host = listen.host();
				int port = listen.port();
				
				if (port == -1) {
					port = ssl != null ? 443 : 80;
				}
				
				String identity = format("%s/%s/%s/ws", registration.applicationIdentity(), host, port);
			
				HttpSettings settings;
				if (ssl != null) {
					settings = HttpSettings.https(port, host, Type.WEBSOCKET, registration.applicationIdentity(), registration.applicationVersion(), ssl);
				} else {
					settings = HttpSettings.http(port, host, Type.WEBSOCKET, registration.applicationIdentity(), registration.applicationVersion());
				}
				
				server.deployWebsocket(identity, settings, ws -> {
					
					topics.forEach(topic -> {
						ws.topic(topic.key());
					});
					
					if (registration.has(connect)) { 
						ws.connect(registration.get(connect));
					}
					
					if (registration.has(disconnect)) {
						ws.disconnect(registration.get(disconnect));
					}
					
					if (registration.has(message)) {
						ws.message(registration.get(message));
					}
					
				});
				
				registration.undeploy(version -> server.undeploy(identity, version));
				
			}
		});
	}

	public class WebsocketSendConfigurer implements OperationConfigurer {

		private Function<Data,String> to;
		private Function<Data,String> messageFn;
		
		@Conf.At("to")
		public void to(String val) {
			to = StringWithVars.compile(val);
		}
		
		@Conf.At("message")
		public void message(String val) {
			messageFn = StringWithVars.compile(val);
		}
		
		@Override
		public void setup(OperationSetup ops) {
			ops.add("msg", store -> new WebsocketSendOperation(store.get(HTTP_SETTINGS), to, messageFn));
		}
		
	}

	public class WebsocketBroadcastConfigurer implements OperationConfigurer {
		
		private Function<Data,String> messageFn;
		
		@Conf.Val
		@Conf.At("message")
		public void message(String val) {
			messageFn = StringWithVars.compile(val);
		}
		
		@Override
		public void setup(OperationSetup ops) {
			ops.add("broadcast", store -> new WebsocketBroadcastOperation(store.get(HTTP_SETTINGS), messageFn));
		}
		
	}

	public class WebsocketTopicSendConfigurer implements OperationConfigurer {
		
		private final IdentityKey<Object> key;
		
		private Function<Data,String> messageFn;
		
		public WebsocketTopicSendConfigurer(IdentityKey<Object> key) {
			this.key = key;
		}
		
		@Conf.Val
		@Conf.At("message")
		public void message(String val) {
			messageFn = StringWithVars.compile(val);
		}
		
		@Override
		public void setup(OperationSetup ops) {
			ops.add(format("%s/send", key.name()), store -> new WebsocketTopicSendOperation(store.get(HTTP_SETTINGS), key, messageFn));
		}
		
	}

	public class WebsocketTopicSubscribeConfigurer implements OperationConfigurer {
		
		private final IdentityKey<Object> key;
		
		private Function<Data,String> idFn;
		
		public WebsocketTopicSubscribeConfigurer(IdentityKey<Object> key) {
			this.key = key;
		}
		
		@Conf.Val
		@Conf.At("id")
		public void message(String val) {
			idFn = StringWithVars.compile(val);
		}
		
		@Override
		public void setup(OperationSetup ops) {
			ops.add(format("%s/subscribe", key.name()), store -> new WebsocketTopicSubscribeOperation(store.get(HTTP_SETTINGS), key, idFn));
		}
		
	}
	
	public class WebsocketSendOperation implements Operation {

		private final Function<Data,String> toFn;
		private final Function<Data,String> messageFn;
		private final HttpSettings settings;
		
		public WebsocketSendOperation(HttpSettings settings, Function<Data,String> toFn, Function<Data,String> messageFn) {
			this.toFn = toFn;
			this.messageFn = messageFn;
			this.settings = settings;
		}
		
		@Override
		public void call(MutableData data) {
			server.websocket(settings, ws -> {
				ws.channel(toFn.apply(data)).ifPresent(channel -> {
					if (channel.isOpen()) {
						channel.writeAndFlush(new TextWebSocketFrame(messageFn.apply(data)));
					}
				});
			});
		}
		
	}
	
	public class WebsocketBroadcastOperation implements Operation {

		private final Function<Data,String> messageFn;
		private final HttpSettings settings;
		
		public WebsocketBroadcastOperation(HttpSettings settings, Function<Data,String> messageFn) {
			this.messageFn = messageFn;
			this.settings = settings;
		}
		
		@Override
		public void call(MutableData data) {
			server.websocket(settings, ws -> {
				ws.channels.forEach((id, ch) -> {
					if (ch.isOpen()) {
						ch.writeAndFlush(new TextWebSocketFrame(messageFn.apply(data)));
					}
				});
			});
		}
		
	}

	public class WebsocketTopicSendOperation implements Operation {

		private final IdentityKey<Object> key;
		private final Function<Data,String> messageFn;
		private final HttpSettings settings;
		
		public WebsocketTopicSendOperation(HttpSettings settings, IdentityKey<Object> key, Function<Data,String> messageFn) {
			this.key = key;
			this.messageFn = messageFn;
			this.settings = settings;
		}
		
		@Override
		public void call(MutableData data) {
			server.websocket(settings, ws -> {
				ws.topic(key).ifPresent(topic -> {
					topic.channels().forEach(channel -> {
						if (channel.isOpen()) {
							channel.writeAndFlush(new TextWebSocketFrame(messageFn.apply(data)));	
						}
					});
				});
			});
		}
		
	}
	
	public class WebsocketTopicSubscribeOperation implements Operation {

		private final IdentityKey<Object> key;
		private final Function<Data,String> idFn;
		private final HttpSettings settings;
		
		public WebsocketTopicSubscribeOperation(HttpSettings settings, IdentityKey<Object> key, Function<Data,String> idFn) {
			this.key = key;
			this.idFn = idFn;
			this.settings = settings;
		}
		
		@Override
		public void call(MutableData data) {
			server.websocket(settings, ws -> {
				ws.channel(idFn.apply(data)).ifPresent(channel -> {
					if (channel.isOpen()) {
						ws.topic(key).ifPresent(topic -> {
							topic.register(channel);
						});
					}
				});
			});
		}
		
	}
	
	public class WebsocketStatusProvider implements StatusDataProvider {

		private final HttpSettings settings;
		
		public WebsocketStatusProvider(HttpSettings settings) {
			this.settings = settings;
		}
		
		@Override
		public boolean up() {
			return true;
		}

		@Override
		public void statusData(MutableData data) {
			server.websocket(settings, ws -> {
				data.putInt("connections", ws.channels.size());
			});
		}
		
	}
	
}