package reka.net.common.sockets;

import io.netty.channel.group.ChannelMatcher;
import io.netty.channel.group.ChannelMatchers;

import java.util.function.Function;

import reka.app.Application;
import reka.config.configurer.annotations.Conf;
import reka.data.Data;
import reka.identity.Identity;
import reka.module.setup.OperationConfigurer;
import reka.module.setup.OperationSetup;
import reka.net.ChannelAttrs;
import reka.net.ChannelAttrs.AttributeMatcher;
import reka.net.NetManager;
import reka.util.StringWithVars;

public class SocketBroadcastConfigurer implements OperationConfigurer {
	
	private final NetManager server;
	private Function<Data,String> messageFn;
	private Function<Data,ChannelMatcher> matcherFn;
	
	public SocketBroadcastConfigurer(NetManager server) {
		this.server = server;			
	}
	
	@Conf.Val
	@Conf.At("message")
	public void message(String val) {
		messageFn = StringWithVars.compile(val);
	}
	
	@Conf.At("exclude")
	public void exclude(String val) {
		StringWithVars idFn = StringWithVars.compile(val);
		if (idFn.hasVariables()) {
			matcherFn = data -> ChannelMatchers.invert(new AttributeMatcher<>(ChannelAttrs.id, idFn.apply(data)));	
		} else {
			ChannelMatcher matcher = ChannelMatchers.invert(new AttributeMatcher<>(ChannelAttrs.id, val));
			matcherFn = data -> matcher;
		}
	}
	
	@Override
	public void setup(OperationSetup ops) {
		ops.add("broadcast", () -> {
			Identity identity = ops.ctx().get(Application.IDENTITY);
			ChannelMatcher identityMatcher = new AttributeMatcher<>(ChannelAttrs.identity, identity);
			if (matcherFn != null) {
				matcherFn = matcherFn.andThen(m -> ChannelMatchers.compose(identityMatcher, m));
			} else {
				matcherFn = data -> identityMatcher;
			}
			return new SocketBroadcastWithMatcherOperation(server, messageFn, matcherFn);
		});
	}
	
}