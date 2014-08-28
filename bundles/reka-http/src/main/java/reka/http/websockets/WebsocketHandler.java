package reka.http.websockets;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reka.api.IdentityKey;
import reka.api.content.Content;
import reka.api.data.Data;
import reka.api.data.MutableData;
import reka.api.flow.Flow;
import reka.api.run.EverythingSubscriber;
import reka.core.data.memory.MutableMemoryData;
import reka.http.server.HttpServerManager.WebsocketTriggers;

@ChannelHandler.Sharable
public class WebsocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
	
	private final Logger log = LoggerFactory.getLogger(getClass());

	public static final AttributeKey<String> hostAttr = AttributeKey.valueOf("host");
	
	private static final AttributeKey<String> idAttr = AttributeKey.valueOf("id");
	
	private final ConcurrentMap<String,WebsocketHost> hosts = new ConcurrentHashMap<>();
	
	public static class WebsocketHost {
		public final ConcurrentMap<IdentityKey<Object>,Topic> topics = new ConcurrentHashMap<>(); 
		public final ConcurrentMap<String,Channel> channels = new ConcurrentHashMap<>();
		public final List<Flow> onConnect = new ArrayList<>();
		public final List<Flow> onDisconnect = new ArrayList<>();
		public final List<Flow> onMessage = new ArrayList<>();
		
		public Optional<Channel> channel(String id) {
			return Optional.ofNullable(channels.get(id));
		}
		
		public Optional<Topic> topic(IdentityKey<Object> key) {
			return Optional.ofNullable(topics.get(key));
		}
	}
	
	public static class Topic {
		public final List<Channel> channels = Collections.synchronizedList(new ArrayList<>());
	}

	public static interface WebsocketListener {
		void connect();
		void disconnect();
		void message(String msg);
	}
	
	public boolean hostExists(String host) {
		return hosts.containsKey(host);
	}
	
	public boolean isEmpty() {
		return hosts.isEmpty();
	}
	
	public void add(String host, WebsocketTriggers d) {
		log.debug("adding ws host [{}]", host);
		WebsocketHost h = hosts.computeIfAbsent(host, (val) -> new WebsocketHost());
		
		h.topics.clear();
		d.topicKeys().forEach(key -> {
			h.topics.put(key, new Topic());
		});
		
		h.onConnect.clear();
		h.onConnect.addAll(d.onConnect());
		
		h.onDisconnect.clear();
		h.onDisconnect.addAll(d.onDisconnect());
		
		h.onMessage.clear();
		h.onMessage.addAll(d.onMessage());
		
	}
	
	public boolean remove(String host) {
		log.debug("removing ws host [{}]", host);
		WebsocketHost h = hosts.remove(host);
		if (h != null) {
			h.channels.forEach((id, ch) -> ch.disconnect());
			return true;
		} else {
			return false;
		}
	}
	
	public void reset(String host) {
		log.debug("restting ws host [{}]", host);
		WebsocketHost h = hosts.get(host);
		if (h != null) {
			//h.channels.forEach((id, ch) -> ch.disconnect());
			//h.channels.clear();
			h.onConnect.clear();
			h.onDisconnect.clear();
			h.onMessage.clear();
		}
	}
	
	public void broadcast(String host, String msg) {
		WebsocketHost h = hosts.get(host);
		if (h == null || h.channels.isEmpty()) return;
        for (Entry<String, Channel> entry : h.channels.entrySet()) {
        	Channel channel = entry.getValue();
        	if (!channel.isOpen()) continue;
        	channel.writeAndFlush(new TextWebSocketFrame(msg));
        }
	}

	public void topicSend(String host, IdentityKey<Object> topicKey, String msg) {
		WebsocketHost h = hosts.get(host);
		if (h == null || h.channels.isEmpty()) return;
		Topic topic = h.topics.get(topicKey);
		if (topic == null) return;
		topic.channels.forEach(channel -> channel.writeAndFlush(new TextWebSocketFrame(msg)));
	}
	
	public void forHost(String host, Consumer<WebsocketHost> c) {
		WebsocketHost h = hosts.get(host);
		if (h != null) c.accept(h);
	}
	
	public void topicSubscribe(String host, IdentityKey<Topic> topicKey, String id) {
		WebsocketHost h = hosts.get(host);
		if (h == null || h.channels.isEmpty()) return;
		Channel channel = h.channels.get(id);
		if (channel == null || !channel.isOpen()) return;
		Topic topic = h.topics.get(topicKey);
		if (topic == null) return;
		topic.channels.add(channel);
	}

	public void send(String host, String to, String msg) {
		WebsocketHost h = hosts.get(host);
		if (h == null || h.channels.isEmpty()) return;
		Channel channel = h.channels.get(to);
		if (channel == null || !channel.isOpen()) return;
		channel.writeAndFlush(new TextWebSocketFrame(msg));
	}
	
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		super.userEventTriggered(ctx, evt);
		if (evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
			String host = ctx.attr(hostAttr).get();
			
			String id = UUID.randomUUID().toString();
			ctx.attr(idAttr).set(id);
			hosts.get(host).channels.put(id, ctx.channel());
			
			log.debug("{} connected to host {}!", id, host);

			for (Flow flow : hosts.get(host).onConnect) {
				
				MutableData data = MutableMemoryData.create();
				
				data.putString("host", host);
				data.putString("id", id);
				
				flow.run(ctx.executor(), data, new EverythingSubscriber(){

					@Override
					public void ok(MutableData data) {
						Optional<Content> o = data.getContent("reply");
						if (o.isPresent()) {
							ctx.channel().writeAndFlush(new TextWebSocketFrame(o.get().asUTF8()));
						}
					}

					@Override
					public void halted() {
					}

					@Override
					public void error(Data data, Throwable t) {
					}
					
				});
			}
			
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		String id = ctx.attr(idAttr).get();
		String host = ctx.attr(hostAttr).get();
		log.debug("{} disconnected", id);
		WebsocketHost wshost = hosts.get(host);
		if (wshost != null) {
			
			wshost.channels.remove(id);
		
			for (Flow flow : wshost.onDisconnect) {
				
				MutableData data = MutableMemoryData.create();
				
				data.putString("host", host);
				data.putString("id", id);
				
				flow.run(ctx.executor(), data, new EverythingSubscriber(){
	
					@Override
					public void ok(MutableData data) {
					}
	
					@Override
					public void halted() {
					}
	
					@Override
					public void error(Data data, Throwable t) {
					}
					
				});
			}

		}
		
	}

	@Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
		String id = ctx.attr(idAttr).get();
		String host = ctx.attr(hostAttr).get();
		
		for (Flow flow : hosts.get(host).onMessage) {
		
			MutableData data = MutableMemoryData.create();
			
			data.putString("host", host);
			data.putString("id", id);
			data.putString("message", frame.text());
			
			flow.run(ctx.executor(), data, new EverythingSubscriber(){

				@Override
				public void ok(MutableData data) {
					Optional<Content> o = data.getContent("response");
					if (o.isPresent()) {
						ctx.channel().writeAndFlush(new TextWebSocketFrame(o.get().asUTF8()));
					}
				}

				@Override
				public void halted() {
				}

				@Override
				public void error(Data data, Throwable t) {
				}
				
			});
		
		}
		
		
		/*
		String name = ctx.attr(nameAttr).get();
		if (name == null) {
			name = frame.text();
			ctx.attr(nameAttr).set(name);
			logger.info("added {}", name);
			ctx.channel().writeAndFlush(new TextWebSocketFrame(format("welcome %s!", name)));
			broadcast(host, "%s joined the room", name);
		} else {
			broadcast(host, "%s: %s", name, frame.text());
		}
		*/
    }
    
}