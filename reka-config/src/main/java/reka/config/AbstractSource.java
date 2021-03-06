package reka.config;

import java.io.File;
import java.nio.file.Path;

public abstract class AbstractSource implements Source {
    

	@Override
	public int originOffsetStart() {
		return 0;
	}

	@Override
	public int originOffsetLength() {
		return content().length();
	}
	
	@Override
	public Source rootOrigin() {
	    Source origin = origin();
	    while (origin.parent() != null) {
	        origin = origin.parent().origin();
	    }
	    return origin;
	}

	@Override
	public boolean supportsNestedFile() {
		return false;
	}

	@Override
	public Path nestedFile(String location) {
		return null;
	}
	
	@Override
	public SourceLinenumbers linenumbers() {
		return SourceUtils.linenumbersFor(content());
	}

	@Override
	public boolean isConstrained() {
		return false;
	}

	@Override
	public File constraint() {
		return null;
	}


}
