package reka.core.bundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static reka.api.Path.slashes;
import static reka.config.configurer.Configurer.configure;
import static reka.config.configurer.Configurer.Preconditions.checkConfig;
import static reka.config.configurer.Configurer.Preconditions.invalidConfig;
import static reka.core.builder.FlowSegments.par;
import static reka.core.builder.FlowSegments.seq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import reka.api.IdentityStore;
import reka.api.Path;
import reka.api.flow.Flow;
import reka.api.flow.FlowSegment;
import reka.config.Config;
import reka.config.configurer.annotations.Conf;
import reka.core.builder.FlowVisualizer;
import reka.core.builder.SingleFlow;
import reka.core.bundle.ModuleSetup.FlowSegmentBiFunction;
import reka.core.bundle.ModuleSetup.TriggerCollection;
import reka.core.runtime.NoFlow;
import reka.core.runtime.NoFlowVisualizer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public abstract class ModuleConfigurer {
	
	public static class ModuleInitializer {
		
		private final Flow flow;
		private final FlowVisualizer visualizer;
		private final Map<Path,FlowSegmentBiFunction> providers;
		private final List<TriggerCollection> triggers;
		private final List<Runnable> shutdownHandlers;
		
		ModuleInitializer(Flow initialize, FlowVisualizer visualizer, 
				Map<Path,FlowSegmentBiFunction> providers,
				List<TriggerCollection> triggers,
				List<Runnable> shutdownHandlers) {
			
			this.flow = initialize;
			this.visualizer = visualizer;
			this.providers = ImmutableMap.copyOf(providers);
			this.triggers = ImmutableList.copyOf(triggers);
			this.shutdownHandlers = ImmutableList.copyOf(shutdownHandlers);
			
		}
		
		public Flow flow() {
			return flow;
		}
		
		public FlowVisualizer visualizer() {
			return visualizer;
		}
		
		public Map<Path,FlowSegmentBiFunction> providers() {
			return providers;
		}
		
		public List<TriggerCollection> triggers() {
			return triggers;
		}
		
		public List<Runnable> shutdownHandlers() {
			return shutdownHandlers;
		}
		
	}
	
	public static ModuleInitializer buildInitializer(ModuleConfigurer root) {
		return Utils.process(root);
	}
	
	private static class Utils {
		
		public static ModuleInitializer process(ModuleConfigurer root) {
			
			Set<ModuleConfigurer> all = collect(root, new HashSet<>());
			Set<ModuleConfigurer> toplevel = findTopLevel(all);
			Map<String,ModuleConfigurer> rootsMap = map(root.uses);
			
			resolveNamedDependencies(all, rootsMap);
			
			Map<Path,FlowSegmentBiFunction> providersCollector = new HashMap<>();
			List<TriggerCollection> triggerCollector = new ArrayList<>();
			List<Runnable> shutdownHandlers = new ArrayList<>();
			Map<ModuleConfigurer,FlowSegment> segments = new HashMap<>();
			Map<Integer,IdentityStore> stores = new HashMap<>();
			
			for (ModuleConfigurer module : all) {
				
				IdentityStore store = IdentityStore.createConcurrentIdentityStore();
				
				ModuleSetup init = new ModuleSetup(module.fullPath(), store);
				module.setup(init);
				
				init.buildFlowSegment().ifPresent(segment -> {
					segments.put(module, segment);
				});
				
				providersCollector.putAll(init.providers());
				
				triggerCollector.addAll(init.triggers());
				
				init.shutdownHandlers().forEach(h -> {
					shutdownHandlers.add(() -> h.accept(store));
				});
				
			}
			
			Optional<FlowSegment> segment = buildSegment(toplevel, segments);
			
			Flow flow = new NoFlow();
			FlowVisualizer visualizer = new NoFlowVisualizer();
			
			if (segment.isPresent()) {
				Entry<Flow, FlowVisualizer> entry = SingleFlow.create(Path.path("initialize"), segment.get(), stores);
				flow = entry.getKey();
				visualizer = entry.getValue();	
			}
			
			return new ModuleInitializer(flow, visualizer, providersCollector, triggerCollector, shutdownHandlers);
		}
		
		private static Optional<FlowSegment> buildSegment(Set<ModuleConfigurer> uses, Map<ModuleConfigurer, FlowSegment> built) {
			List<FlowSegment> segments = new ArrayList<>();
			for (ModuleConfigurer use : uses) {
				buildSegment(use, built).ifPresent(segment -> segments.add(segment));
			}
			return segments.isEmpty() ? Optional.empty() : Optional.of(par(segments));
		}
		
		private static Optional<FlowSegment> buildSegment(ModuleConfigurer use, Map<ModuleConfigurer, FlowSegment> built) {
			if (use.isRoot()) return Optional.empty();
			
			List<FlowSegment> sequence = new ArrayList<>();
			
			if (built.containsKey(use)) {
				sequence.add(built.get(use));
			}
			
			Optional<FlowSegment> c = buildSegment(use.usedBy, built);
			if (c.isPresent()) {
				sequence.add(c.get());
			}
			
			return sequence.isEmpty() ? Optional.empty() : Optional.of(seq(sequence));
		}
		
		private static void resolveNamedDependencies(Set<ModuleConfigurer> all, Map<String, ModuleConfigurer> allMap) {
			for (ModuleConfigurer use : all) {
				for (String depname : use.modulesNames) {
					ModuleConfigurer dep = allMap.get(depname);
					checkNotNull(dep, "missing dependency: [%s] uses [%s]", use.name(), depname);
					dep.usedBy.add(use);
					use.uses.add(dep);
				}
			}
		}

		private static Set<ModuleConfigurer> findTopLevel(Collection<ModuleConfigurer> uses) {
			Set<ModuleConfigurer> roots = new HashSet<>();
			for (ModuleConfigurer use : uses) {
				if (use.uses.isEmpty()) {
					roots.add(use);
				}
			}
			return roots;
		}
		
		private static Set<ModuleConfigurer> collect(ModuleConfigurer use, Set<ModuleConfigurer> collector) {
			collector.add(use);
			for (ModuleConfigurer child : use.uses) {
				collect(child, collector);
			}
			return collector;
		}
		
		public static Map<String,ModuleConfigurer> map(Collection<ModuleConfigurer> uses) {
			Map<String,ModuleConfigurer> map = new HashMap<>();
			for (ModuleConfigurer use : uses) {
				map.put(use.name(), use);
			}
			return map;
		}
		
	}
	
	private List<Entry<Path, Supplier<ModuleConfigurer>>> modules = new ArrayList<>();
	
	private String type;
	private String name;
	
	private boolean isRoot;
	
	private Path parentPath = Path.root();
	private Path modulePath = Path.root();
	
	private final List<String> modulesNames = new ArrayList<>();
	
	private final Set<ModuleConfigurer> usedBy = new HashSet<>();
	private final Set<ModuleConfigurer> uses = new HashSet<>();

	public ModuleConfigurer modules(List<Entry<Path, Supplier<ModuleConfigurer>>> modules) {
		this.modules = modules;
		
		if (isRoot()) {
			findRootConfigurers();
		}
		
		return this;
	}

	private void findRootConfigurers() {
		for (Entry<Path, Supplier<ModuleConfigurer>> e : modules) {
			// all the ones with a root path need to be added automatically
			// we don't need to explicitly load these...
			if (e.getKey().isEmpty()) {
				uses.add(e.getValue().get());
			}
		}
	}

	public abstract void setup(ModuleSetup module);
	
	public boolean isRoot() {
		return isRoot;
	}
	
	public ModuleConfigurer isRoot(boolean val) {
		isRoot = val;
		return this;
	}
	
	public ModuleConfigurer modulePath(Path path) {
		this.modulePath = path;
		return this;
	}

	@Conf.Key
	public ModuleConfigurer type(String val) {
		type = val;
		return this;
	}
	
	@Conf.Val
	public ModuleConfigurer name(String val) {
		name = val;
		return this;
	}
	
	public String name() {
		return name != null ? name : type;
	}
	
	public String getName() {
		return name();
	}
	
	private Path fullPath() {
		return parentPath.add(slashes(name()));
	}
	
	public String typeAndName() {
		if (type.equals(name())) {
			return type;
		} else {
			return format("%s/%s", type, name());
		}
	}
	
	public void useThisConfig(Config config) {
		checkConfig(modules != null, "'%s' is not a valid module (try one of %s)", config.key(), mappingNames());
		Supplier<ModuleConfigurer> supplier = mappingFor(slashes(config.key()));
		checkConfig(supplier != null, "'%s' is not a valid module (try one of %s)", config.key(), mappingNames());
		configureModule(supplier.get(), config);
	}
	
	protected void configureModule(ModuleConfigurer module, Config config) {
		module.modules(modules);
		module.parentPath(modulePath);
		configure(module, config);
		uses.add(module);
		module.usedBy.add(this);
	}
	
	@Conf.Each("use")
	public void use(Config config) {
		if (config.hasBody()) {
			for (Config childConfig : config.body()) {
				useThisConfig(childConfig);
			}
		} else if (config.hasValue()) {
			modulesNames.add(config.valueAsString());
		} else {
			invalidConfig("must have body or value (referencing another dependency)");
		}
	}
	
	private void parentPath(Path val) {
		parentPath = val;
	}

	private Supplier<ModuleConfigurer> mappingFor(Path path) {
		for (Entry<Path,Supplier<ModuleConfigurer>> e : modules) {
			if (e.getKey().equals(path)) {
				return e.getValue();
			}
		}
		return null;
	}
	
	private Collection<String> mappingNames() {
		List<String> result = new ArrayList<>();
		for (Entry<Path,Supplier<ModuleConfigurer>> e : modules) {
			if (!e.getKey().isEmpty()) {
				result.add(e.getKey().slashes());
			}
		}
		return result;
	}
	
	@Override
	public String toString() {
		return format("%s(\n    name %s\n    params %s)", type, name(), modulesNames);
	}

}
