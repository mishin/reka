package reka.test.config;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static reka.config.ConfigTestUtil.loadconfig;
import static reka.config.configurer.Configurer.configure;
import static reka.util.Path.dots;
import static reka.util.Util.runtime;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;

import reka.config.Config;
import reka.config.NavigableConfig;
import reka.config.configurer.Configurer.InvalidConfigurationException;
import reka.data.Data;
import reka.data.MutableData;
import reka.data.memory.MutableMemoryData;
import reka.flow.FlowNode;
import reka.flow.FlowOperation;
import reka.flow.FlowSegment;
import reka.flow.ops.Operation;
import reka.flow.ops.OperationContext;
import reka.flow.ops.RouterOperation;
import reka.identity.IdentityStore;
import reka.module.setup.ModuleSetupContext;
import reka.module.setup.OperationConfigurer;
import reka.module.setup.OperationSetup;
import reka.modules.builtins.BuiltinsConfigurer;
import reka.runtime.DefaultRouteCollector;
import reka.util.Path;
import reka.util.Path.PathElement;

public class PutTest {
	
	private final NavigableConfig root;
	
	public PutTest() {
		root = loadconfig("/put.reka");
	}
	
	@Test
	public void simple() {
		assertThat(configurePutWith("simple.put").at("name").content().asUTF8(), equalTo("nick"));
	}
	
	@Test
	public void subkey() {
		assertThat(configurePutWith("subkey.put").at("name").content().asUTF8(), equalTo("nick"));
	}
	
	@Test
	public void nested() {
		assertThat(configurePutWith("nested.put").at(dots("name.first")).content().asUTF8(), equalTo("nick"));
	}
	
	@Test(expected=InvalidConfigurationException.class)
	public void broken() {
		configurePutWith("invalid.put");
	}
	
	@Test
	public void withValAndBody() {
		Data data = configurePutWith("with-val-and-body.put");
		assertThat(data.at(dots("yay.name")).content().asUTF8(), equalTo("peter"));
		assertThat(data.at(dots("yay.age")).content().asUTF8(), equalTo("20"));
		assertThat(data.at(dots("yay.inner.@")).content().asUTF8(), equalTo("wooo"));
		assertThat(data.at(dots("yay.inner.it")).content().asUTF8(), equalTo("should"));
		assertThat(data.at(dots("yay.inner.work")).content().asUTF8(), equalTo("ok"));
	}
	
	@Test
	public void arrayIntent() {
		Data data = configurePutWith("array-intent.put");
		System.out.println(data.toPrettyJson());
		assertTrue(data.at("people").isList());
		Collection<PathElement> es = data.at("people").elements();
		assertThat(es.size(), equalTo(4));
		assertThat(data.at(dots("people[0]")).content().asUTF8(), equalTo("james"));
		assertThat(data.at(dots("people[1]")).content().asUTF8(), equalTo("andrew"));
		assertThat(data.at(dots("people[2]")).content().asUTF8(), equalTo("woah"));
		assertThat(data.at(dots("people[3]")).content().asUTF8(), equalTo("nick"));
	}
	
	private Data configurePutWith(String path) {
		MutableData data = MutableMemoryData.create();
		configureThenCall(new BuiltinsConfigurer.PutConfigurer(), root.at(path).get(), data);
		return data;
	}
	
	private static void configureThenCall(OperationConfigurer s, Config config, MutableData data) {
		OperationSetup collector = OperationSetup.createSequentialCollector(Path.root(), new ModuleSetupContext(IdentityStore.createConcurrentIdentityStore()));
		configure(s, config);
		
		s.setup(collector);
		FlowSegment v = collector.get();
		
		FlowOperation op = firstNode(v).operationSupplier().get();
		if (op instanceof Operation) {
			callSync((Operation) op, data, new OperationContext(IdentityStore.emptyReader()));
		} else if (op instanceof RouterOperation) {
			callRouting((RouterOperation) op, data);
		} else {
			throw runtime("couldn't work out %s", op);
		}
	}
	
	private static FlowNode firstNode(FlowSegment segment) {
		if (segment.isNode()) {
			return segment.node();
		} else {
			for (FlowSegment s : segment.segments()) {
				if (s.equals(segment)) continue;
				FlowNode n = firstNode(s);
				if (n != null) {
					return n;
				}
			}
		}
		return null;
	}

	private static void callSync(Operation op, MutableData data, OperationContext ctx) {
		op.call(data, ctx);
	}
	
	private static void callRouting(RouterOperation op, MutableData data) {
		op.call(data, DefaultRouteCollector.create(new ArrayList<>()));
	}

}
