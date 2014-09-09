package reka.twilio;

import static reka.api.Path.dots;
import static reka.api.Path.path;
import static reka.util.Util.unchecked;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reka.api.Path;
import reka.api.data.Data;
import reka.api.data.MutableData;
import reka.api.run.AsyncOperation;
import reka.api.run.Operation;
import reka.config.configurer.annotations.Conf;
import reka.core.bundle.RekaBundle;
import reka.core.setup.ModuleConfigurer;
import reka.core.setup.ModuleSetup;
import reka.core.setup.OperationSetup;
import reka.core.util.StringWithVars;
import reka.nashorn.OperationConfigurer;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.SmsFactory;
import com.twilio.sdk.resource.instance.Sms;

public class TwilioBundle implements RekaBundle {
	
	private static final Logger log = LoggerFactory.getLogger(TwilioBundle.class);

	public void setup(BundleSetup bundle) {
		bundle.module(path("twilio"), () -> new TwilioModule());
	}
	
	public static class TwilioModule extends ModuleConfigurer {
		
		private String sid;
		private String token;
		private String defaultFrom;
		
		@Conf.At("sid")
		public void sid(String val) {
			sid = val;
		}
		
		@Conf.At("token")
		public void token(String val) {
			token = val;
		}
		
		@Conf.At("default-from")
		public void defaultFrom(String val) {
			defaultFrom = val;
		}

		@Override
		public void setup(ModuleSetup module) {
			module.operation(path("send"), provider -> new TwilioSendConfigurer(sid, token, defaultFrom));
		}
		
	}
	
	public static class TwilioSendConfigurer implements OperationConfigurer {
		
		private final String sid;
		private final String token;
		private final String defaultFrom;
		
		private Function<Data,String> msgFn;
		private Function<Data,String> toFn;
		
		private Path out = dots("twilio.response");
		
		public TwilioSendConfigurer(String sid, String token, String defaultFrom) {
			this.sid = sid;
			this.token = token;
			this.defaultFrom = defaultFrom;
		}
		
		@Conf.Val
		@Conf.At("to")
		public void to(String val) {
			toFn = StringWithVars.compile(val);
		}
		
		@Conf.At("msg")
		public void msg(String val) {
			msgFn = StringWithVars.compile(val);
		}
		
		@Conf.At("out")
		public void out(String val) {
			out = dots(val);
		}

		@Override
		public void setup(OperationSetup ops) {
			ops.add("send sms", store -> new TwilioSendOperation(sid, token, defaultFrom, msgFn, toFn, out));
		}
		
	}
	
	public static class TwilioSendOperation2 implements AsyncOperation {

		private final String sid;
		private final String token;
		private final String defaultFrom;
		private final Function<Data,String> msgFn;
		private final Function<Data,String> toFn;
		private final Path out;
		
		public TwilioSendOperation2(String sid, String token, String defaultFrom, Function<Data,String> msgFn, Function<Data,String> toFn, Path out) {
			this.sid = sid;
			this.token = token;
			this.defaultFrom = defaultFrom;
			this.msgFn = msgFn;
			this.toFn = toFn;
			this.out = out;
		}
		
		@Override
		public void run(MutableData data, OperationResult ctx) {
			TwilioRestClient client = new TwilioRestClient(sid, token);
			 
		    Map<String, String> params = new HashMap<String, String>();
		    params.put("Body", msgFn.apply(data));
		    params.put("To", toFn.apply(data));
		    params.put("From", defaultFrom);
		 
		    SmsFactory messageFactory = client.getAccount().getSmsFactory();
		    
			try {
				Sms message = messageFactory.create(params);
				
				data.putMap(out, map -> {
					//map.putString("price", message.getPrice());
					map.putString("sid", message.getAccountSid());
					map.putString("body", message.getBody());
					//map.putString("date-sent", message.getDateSent().toString());
					map.putString("direction", message.getDirection());
					map.putString("to", message.getTo());
					map.putString("from", message.getFrom());
					map.putString("status", message.getStatus());
				});
				
			    log.debug(message.getSid());
			} catch (TwilioRestException e) {
				throw unchecked(e);
			}
			
			ctx.done();
			
		}
		
	}
	
	public static class TwilioSendOperation implements Operation {
		
		private final String sid;
		private final String token;
		private final String defaultFrom;
		private final Function<Data,String> msgFn;
		private final Function<Data,String> toFn;
		private final Path out;
		
		public TwilioSendOperation(String sid, String token, String defaultFrom, Function<Data,String> msgFn, Function<Data,String> toFn, Path out) {
			this.sid = sid;
			this.token = token;
			this.defaultFrom = defaultFrom;
			this.msgFn = msgFn;
			this.toFn = toFn;
			this.out = out;
		}

		@Override
		public void call(MutableData data) {
			TwilioRestClient client = new TwilioRestClient(sid, token);
			 
		    Map<String, String> params = new HashMap<String, String>();
		    params.put("Body", msgFn.apply(data));
		    params.put("To", toFn.apply(data));
		    params.put("From", defaultFrom);
		 
		    SmsFactory messageFactory = client.getAccount().getSmsFactory();
		    
			try {
				Sms message = messageFactory.create(params);
				
				data.putMap(out, map -> {
					//map.putString("price", message.getPrice());
					map.putString("sid", message.getAccountSid());
					map.putString("body", message.getBody());
					//map.putString("date-sent", message.getDateSent().toString());
					map.putString("direction", message.getDirection());
					map.putString("to", message.getTo());
					map.putString("from", message.getFrom());
					map.putString("status", message.getStatus());
				});
				
			    log.debug(message.getSid());
			} catch (TwilioRestException e) {
				throw unchecked(e);
			}
		}
		
		
	}
	
}
