package reka.data.storage;

import java.io.Closeable;

public interface DataStorage extends DataStorageReader, Closeable {
	public DataStorageCommit createCommit();
}
