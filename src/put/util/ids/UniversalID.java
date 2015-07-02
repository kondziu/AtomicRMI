package put.util.ids;

import java.io.Serializable;
import java.util.UUID;

public interface UniversalID extends Comparable<UniversalID>, Serializable {
	String label();
	UUID uuid();
}
