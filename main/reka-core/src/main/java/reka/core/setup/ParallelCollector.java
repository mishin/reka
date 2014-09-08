package reka.core.setup;

import static reka.core.builder.FlowSegments.par;

import java.util.Collection;

import reka.api.IdentityStore;
import reka.api.flow.FlowSegment;

class ParallelCollector extends AbstractOperationCollector {

	public ParallelCollector(IdentityStore store) {
		super(store);
	}

	@Override
	FlowSegment build(Collection<FlowSegment> segments) {
		return par(segments);
	}
	
}