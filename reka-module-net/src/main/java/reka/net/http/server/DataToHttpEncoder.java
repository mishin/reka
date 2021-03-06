package reka.net.http.server;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static reka.data.MoreDataUtils.writeToOutputStreamAsJson;
import static reka.data.MoreDataUtils.writeToOutputStreamAsPrettyJson;
import static reka.util.Util.unchecked;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.netty.handler.stream.ChunkedFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reka.Reka;
import reka.data.Data;
import reka.data.content.Content;
import reka.util.Path.Request;
import reka.util.Path.Response;

@Sharable
public class DataToHttpEncoder extends MessageToMessageEncoder<Data> {

	public static final DataToHttpEncoder NORMAL = new DataToHttpEncoder(false);
	public static final DataToHttpEncoder SSL = new DataToHttpEncoder(true);

	private static final String DEFAULT_SERVER_NAME = "reka-http";

	private static final byte[] NEW_LINE = "\n".getBytes(StandardCharsets.UTF_8);

	private static final String TEXT_PLAIN = "text/plain";
	private static final String APPLICATION_JSON = "application/json";

	private final Logger logger = LoggerFactory.getLogger("http-encoder");
	private final boolean ssl;

	private static volatile CharSequence date;
	private static final SimpleDateFormat sdf = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
	private static final Runnable setdate = () -> date = HttpHeaders.newEntity(sdf.format(new Date()));

	static {
		setdate.run();
		Reka.SharedExecutors.scheduled.scheduleWithFixedDelay(setdate, 1, 1, TimeUnit.SECONDS);
	}

	private DataToHttpEncoder(boolean ssl) {
		this.ssl = ssl;
	}

	@Override
	protected void encode(ChannelHandlerContext context, Data data, List<Object> out) throws Exception {

		try {
			
			HttpResponseStatus responseStatus = null;
			ByteBuf buffer = null;
			File file = null;
			String contentType = null;

			Data maybeContent = data.at(Response.CONTENT);

			boolean headRequest = data.existsAt(Response.HEAD);

			if (headRequest) {
				logger.info("sending HEAD response");
			}

			Optional<String> status = data.getString(Response.STATUS);
			
			if (status.isPresent()) {
				responseStatus = HttpResponseStatus.valueOf(Integer.valueOf(status.get())); 
			}

			if (maybeContent.isContent()) {

				if (!headRequest) {

					Content content = maybeContent.content();

					switch (content.type()) {
					case BINARY:
						if (content.hasFile()) {
							file = content.asFile();
						} else if (content.hasByteBuffer()) {
							buffer = Unpooled.wrappedBuffer(content.asByteBuffer());
						} else {
							buffer = Unpooled.wrappedBuffer(content.asBytes());
						}
						break;
					case NULL:
						break;
					default:
						buffer = context.alloc().buffer().writeBytes(content.asUTF8().getBytes(StandardCharsets.UTF_8));
						break;
					}

				}
			} else if (maybeContent.isPresent()) {

				Data contentData = maybeContent;

				// send content data json
				buffer = context.alloc().buffer();

				if (data.existsAt(Request.Params.PRETTY)) {
					writeToOutputStreamAsPrettyJson(contentData, new ByteBufOutputStream(buffer));
					buffer.writeBytes(NEW_LINE);
				} else {
					writeToOutputStreamAsJson(contentData, new ByteBufOutputStream(buffer));
				}
				contentType = APPLICATION_JSON;

			} else if (responseStatus == null) {
				// no content
				responseStatus = HttpResponseStatus.NO_CONTENT;
			}
			
			if (responseStatus == null) {
				responseStatus = HttpResponseStatus.OK;
			}

			HttpResponse response;
			if (buffer != null) {
				response = new DefaultFullHttpResponse(HTTP_1_1, responseStatus, buffer);
			} else {
				response = new DefaultHttpResponse(HTTP_1_1, responseStatus);
			}

			response.headers().set(HttpHeaders.Names.SERVER, DEFAULT_SERVER_NAME);
			response.headers().set(HttpHeaders.Names.DATE, date);
			if (data.existsAt(HttpHostHandler.CLOSE_CHANNEL)) {
				response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
			} else {
				response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
			}

			data.at(Response.HEADERS).forEachContent((p, c) -> {
				response.headers().set(p.last().toString(), c);
			});

			if (!response.headers().contains(HttpHeaders.Names.CONTENT_TYPE)) {
				response.headers().set(HttpHeaders.Names.CONTENT_TYPE, TEXT_PLAIN);
			}

			Collection<Cookie> cookies = new ArrayList<>();

			data.at(Response.COOKIES).forEachData((p, d) -> {
				String val = d.isContent() ? d.content().asUTF8() : d.getString("value").get();
				cookies.add(new DefaultCookie(p.toString(), val));
			});

			for (String c : ServerCookieEncoder.encode(cookies)) {
				response.headers().add(HttpHeaders.Names.SET_COOKIE, c);
			}

			if (contentType != null) {
				response.headers().set(HttpHeaders.Names.CONTENT_TYPE, contentType);
			}

			if (response.headers().get(HttpHeaders.Names.CONTENT_LENGTH) == null) {
				if (file != null) {
					response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, file.length());
				} else if (buffer != null) {
					response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
				} else {
					response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, 0);
				}
			}

			out.add(response);

			if (file != null) {
				if (ssl) {
					out.add(new ChunkedFile(file));
					out.add(LastHttpContent.EMPTY_LAST_CONTENT);
				} else {
					try {
						// TODO: handle the 'Range:' header here... :)
						out.add(new DefaultFileRegion(new FileInputStream(file).getChannel(), 0, file.length()));
						out.add(LastHttpContent.EMPTY_LAST_CONTENT);
					} catch (FileNotFoundException e) {
						throw unchecked(e); // not very good...
					}
				}
			} else if (buffer == null) {
				out.add(LastHttpContent.EMPTY_LAST_CONTENT);
			}
		} catch (Throwable t) {
			logger.error("oops!", t);
		}
	}

}
