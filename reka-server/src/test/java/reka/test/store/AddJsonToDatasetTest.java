package reka.test.store;

import static reka.data.content.Contents.binary;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reka.data.MutableData;
import reka.data.memory.MutableMemoryData;

public class AddJsonToDatasetTest {
	
	private final Logger log = LoggerFactory.getLogger(getClass());

	//private static final JsonFactory factory = new SmileFactory();
	private static final JsonFactory factory = new JsonFactory();
	//private static final JsonFactory smileFactory = new SmileFactory();
	
	@Test
	public void handlesBinaryInJson() throws Throwable {
		
		String contentType = "example/custom-content-type";
		byte[] bytes = new byte[] { 5, 6, 24, 12, 35, 0, 99 };
		
		MutableData ds = MutableMemoryData.create();
		ds.put("item", binary(contentType, bytes));
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator json = factory.createJsonGenerator(baos);
		json.useDefaultPrettyPrinter();
		ds.writeJsonTo(json);
		json.flush();
		
		log.debug("json -> {}\n", new String(baos.toByteArray(), StandardCharsets.UTF_8));
		
		/* not made yet TODO: make it and reinstate test!
		Data ds2 = MutableMemoryData.readJson(factory.createJsonParser(baos.toByteArray()));
		
		Content content = ds2.getContent("item").get();
		
		
		
		assertThat(content, is(instanceOf(BinaryContent.class)));
		
		BinaryContent binary = (BinaryContent) content;
		
		assertThat(binary.contentType(), equalTo(contentType));
		assertThat(binary.decoded(), equalTo(bytes));
		*/
		
		
	}
	
}
