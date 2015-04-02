package reka.admin;

import reka.Identity;
import reka.api.Path;
import reka.api.data.MutableData;
import reka.api.run.Operation;
import reka.api.run.OperationContext;
import reka.core.app.Application;
import reka.core.app.manager.ApplicationManager;
import reka.core.data.memory.MutableMemoryData;



public class RekaListOperation implements Operation {
	
	private final ApplicationManager manager;
	private final Path out;
	
	public RekaListOperation(ApplicationManager manager, Path out) {
		this.manager = manager;
		this.out = out;
	}

	@Override
	public void call(MutableData data, OperationContext ctx) {
		data.putList(out, list -> {
			manager.forEach(e -> {
				Identity identity = e.getKey();
				Application app = e.getValue();	
				MutableData item = MutableMemoryData.create();
				item.putString("id", identity.name());
				AdminUtils.putAppDetails(item.createMapAt("app"), app, manager.statusFor(identity));
				list.add(item);
			});
		});
	}

}