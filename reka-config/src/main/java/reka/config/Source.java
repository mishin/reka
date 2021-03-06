package reka.config;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public interface Source {

    String content();
    
    String location();
    
    boolean isFile();
    File file();
    
    boolean isConstrained();
    File constraint();

    boolean supportsNestedFile();
    Path nestedFile(String location);
	List<Path> nestedFiles(String location);
    
    boolean hasParent();
    Source parent();
    
    Source origin();
    Source rootOrigin();
    
    int originOffsetStart();
    int originOffsetLength();
    
    SourceLinenumbers linenumbers();
    
    default Source subset(int offset, int length) {
    	return new SubsetSource(this, offset, length);
    }
    
}
