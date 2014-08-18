package reka.core.builder;

import java.util.Map.Entry;

import reka.api.Path;
import reka.api.data.Data;
import reka.api.flow.Flow;
import reka.api.flow.FlowSegment;

public class SingleFlow {
	
	public static Entry<Flow,FlowVisualizer> create(Path name, FlowSegment segment, Data initializationData) {
		return new FlowsBuilder().add(name, segment).buildAll(initializationData).flowsAndVisualizers().entrySet().iterator().next();
	}

}
