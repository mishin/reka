package reka.flow.builder;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import reka.flow.Flow;
import reka.runtime.Node;
import reka.util.Path;

public class NodeFactory {
    
    private final Map<Integer,NodeBuilder> builders;
    private final Map<Path,Flow> embeddableFlows;
    private final Map<Integer,Node> nodes = new HashMap<>();
    private final StringBuffer dotContent = new StringBuffer();
    
    NodeFactory(Map<Integer,NodeBuilder> builders, Map<Path,Flow> embeddedableFlows) {
        this.builders = builders;
        this.embeddableFlows = embeddedableFlows;
    }
    
    public Node get(int nodeId) {
    	return nodes.computeIfAbsent(nodeId, id -> {
    		NodeBuilder builder = builders.get(id);
            checkNotNull(builder, "no builder for node %d", id);
            return builder.build(this);
    	});
    }
    
    public Flow getFlow(Path name) {
    	Flow flow = embeddableFlows.get(name);
    	if (flow == null) {
    		for (Entry<Path, Flow> e : embeddableFlows.entrySet()) {
    			if (e.getKey().endsWith(name)) {
    				flow = e.getValue();
    				break;
    			}
    		}
    	}
    	checkNotNull(flow, "missing flow [%s]", name);
    	return flow;
    }
    
    public Collection<Node> nodes() {
        return nodes.values();
    }
    
    public StringBuffer dot() {
    	return dotContent;
    }
    
    public String toDot() {
    	return format("diagraph G {\n%s}\n", dotContent.toString());
    }
    
}