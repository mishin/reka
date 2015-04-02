package reka.irc;

import java.util.function.Function;

import reka.api.data.Data;
import reka.api.data.MutableData;
import reka.api.run.Operation;
import reka.api.run.OperationContext;

public class IrcSendOperation implements Operation {

	private final RekaBot bot;
	private final Function<Data,String> msgFn;
	
	public IrcSendOperation(RekaBot bot, Function<Data,String> msgFn) {
		this.bot = bot;
		this.msgFn = msgFn;
	}
	
	@Override
	public void call(MutableData data, OperationContext ctx) {
		bot.send(msgFn.apply(data));
	}

}