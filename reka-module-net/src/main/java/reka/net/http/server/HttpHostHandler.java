package reka.net.http.server;

import static java.util.Collections.synchronizedList;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;
import static reka.data.content.Contents.integer;
import static reka.util.Path.dots;
import static reka.util.Util.createEntry;
import static reka.util.Util.unwrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reka.data.Data;
import reka.data.MutableData;
import reka.data.memory.MutableMemoryData;
import reka.flow.Flow;
import reka.flow.ops.Subscriber;
import reka.identity.IdentityStore;
import reka.identity.ImmutableIdentityStore.Builder;
import reka.net.NetModule;
import reka.util.Path;
import reka.util.Path.Request;
import reka.util.Path.Response;

@ChannelHandler.Sharable
public class HttpHostHandler extends SimpleChannelInboundHandler<MutableData> {

	public static final Path CLOSE_CHANNEL = dots("options.close");
	
	private static final Logger log = LoggerFactory.getLogger(HttpHostHandler.class);
	
	private final ConcurrentMap<String,List<Entry<Flow,Consumer<Flow>>>> paused = new ConcurrentHashMap<>();
	
	private final ConcurrentMap<String,Flow> flows = new ConcurrentHashMap<>();
	
	public boolean isEmpty() {
		return flows.isEmpty();
	}
	
	public HttpHostHandler add(String host, Flow flow) {
		flows.put(host, flow);
		List<Entry<Flow,Consumer<Flow>>> waiting = paused.remove(host);
		if (waiting != null) {
			synchronized (waiting) {
				log.info("resuming {} connections for host {} from newly deployed flow", waiting.size(), host);
				for (Entry<Flow, Consumer<Flow>> c : waiting) {
					c.getValue().accept(flow);
				}
			}
		}
		return this;
	}

	public boolean remove(String host) {
		if (flows.remove(host) != null) {
			log.info("removed host {}", host);
			paused.remove(host);
			return true;
		}
		return false;
	}

	public void pause(String host) {
		paused.putIfAbsent(host, synchronizedList(new ArrayList<>()));
	}


	public void resume(String host) {
		List<Entry<Flow,Consumer<Flow>>> waiting = paused.remove(host);
		if (waiting != null) {
			synchronized (waiting) {
				log.info("resuming {} connections for host {} from existing flow", waiting.size(), host);
				for (Entry<Flow, Consumer<Flow>> c : waiting) {
					c.getValue().accept(c.getKey());
				}
			}
		}
	}

	private Flow flowForHost(String host) {
		Flow flow = flows.get(host);
		return flow != null ? flow : flows.get("*");
	}
	
	private static class ChannelHandlerContextDataSubscriber implements Subscriber {

		private final long started = System.nanoTime();
		private final ChannelHandlerContext context;
		
		ChannelHandlerContextDataSubscriber(ChannelHandlerContext context) {
			this.context = context;
		}
		
		@Override
		public void ok(MutableData data) {
			ChannelFuture writeFuture = context.writeAndFlush(data);
			long took = (System.nanoTime() - started) / 1000;
			String status = data.getString(Response.STATUS).orElse("-");
			log.info("{} - \"{} {}\" {}us", 
					data.getString(Request.HOST).orElse(""), 
					data.getString(Request.METHOD).orElse(""), 
					data.getString(Request.PATH).orElse(""), 
					status,
					took);
			if (data.existsAt(CLOSE_CHANNEL)) {
				writeFuture.addListener(ChannelFutureListener.CLOSE);
			}
		}

		@Override
		public void halted() {
			context.writeAndFlush(MutableMemoryData.create()
					.put(Response.STATUS, integer(404)))
				.addListener(ChannelFutureListener.CLOSE);
		}

		@Override
		public void error(Data data, Throwable error) {
			Data msg;
			String acceptHeader = data.getString(Request.Headers.ACCEPT).orElse("");
			if (acceptsHtml(acceptHeader)) {
				msg = htmlErrorMessage(data, error);
			} else if (acceptsJson(acceptHeader)) {
				msg = jsonErrorMessage(data, error);
			} else {
				msg = textErrorMessage(data, error);
			}
			context.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
		}
		
		private static boolean acceptsHtml(String acceptHeader) {
			return acceptHeader.contains("/html");
		}
		
		private static boolean acceptsJson(String acceptHeader) {
			return acceptHeader.contains("/json");
		}
		
		private static String stacktrace(Throwable t) {
			StringWriter writer = new StringWriter();
			t.printStackTrace(new PrintWriter(writer));
			return writer.toString();
		}
		
		private static Data textErrorMessage(Data data, Throwable t) {
			return MutableMemoryData.create()
					.putString(Response.CONTENT, rootExceptionMessage(unwrap(t)))
					.putString(Response.Headers.CONTENT_TYPE, "text/plain")
					.putInt(Response.STATUS, 500);
		}
		
		private static Data jsonErrorMessage(Data data, Throwable t) {
			t = unwrap(t);
			return MutableMemoryData.create()
						.put(Response.CONTENT.add("data"), data)
						.putString(Response.CONTENT.add("message"), rootExceptionMessage(t))
					.putString(Response.Headers.CONTENT_TYPE, "application/json")
					.putInt(Response.STATUS, 500);
		}
		
		private static Data htmlErrorMessage(Data data, Throwable t) {
			t = unwrap(t);
			return MutableMemoryData.create()
					.putString(Response.CONTENT, 
                            "<html><body>" +
                                    "<h1>Oh noes!</h1>" +
                                    "<h2>" +
                                    rootExceptionMessage(t) +
                                    "</h2>" +
                                    "<h3>Stack trace</h3>" +
                                    "<pre>" +
                                    stacktrace(t) +
                                    "</pre>" +
                                    "<h3>Data at time of error</h3>" +
                                    "<pre>" +
                                    escapeHtml4(data.toPrettyJson()) +
                                    "</pre>" +
                                    "</body></html>")
					.putString(Response.Headers.CONTENT_TYPE, "text/html")
					.putInt(Response.STATUS, 500);

		}
		
	}
	
	@Override
	protected void channelRead0(ChannelHandlerContext context, MutableData data) {

		String host = data.getString(Path.Request.HOST).orElse("localhost");

		Flow flow = flowForHost(host);
		
		if (flow == null) {
			noFlowFound(context, data, host);
			return;
		}
		
		if (paused.containsKey(host)) {
			List<Entry<Flow,Consumer<Flow>>> waiting = paused.get(host);
			synchronized (waiting) {
				// now we have the waiting list locked, check we are still frozen
				if (paused.containsKey(host)) {
					waiting.add(createEntry(flow, (newFlow) -> {
						executeFlow(context, newFlow, data, host);
					}));
					return;	
				}
			}
		}
		
		executeFlow(context, flow, data, host);
	}
	
	private void noFlowFound(ChannelHandlerContext context, Data data, String host) {
		log.debug("no flow registered for host {}", host);
		context.close();
	}
	
	private void executeFlow(ChannelHandlerContext context, Flow flow, MutableData data, String host) {
		Builder store = IdentityStore.immutableBuilder();
		store.put(NetModule.Keys.channel, context.channel());
		flow.run(context.executor(), context.executor(), data, new ChannelHandlerContextDataSubscriber(context), store.build(), true);
	}

	private static String rootExceptionMessage(Throwable t) {
		return allExceptionMessages(t).iterator().next();
	}
	
	private static Collection<String> allExceptionMessages(Throwable tOriginal) {
		List<String> result = new ArrayList<>();
		
		Throwable t = tOriginal;
		
		while (t != null) {
			if (t.getMessage() != null) {
				result.add(t.getMessage());
			}
			t = t.getCause();
		}
		
		Collections.reverse(result);
		
		if (result.isEmpty()) {
			result.add("unknown error");
			log.error("error without any messages", tOriginal);
		}
		
		return result;
	}
	
	 @Override
	 public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		 ctx.close();
	 }
	
}
