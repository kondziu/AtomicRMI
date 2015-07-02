package put.util.ids;

import java.util.UUID;

public class SimpleID implements UniversalID {

	private static final long serialVersionUID = 5484947689859957913L;
	
	private final UUID uuid;
	private final String label; // TODO transient

	public SimpleID() {
		this(UUID.randomUUID());
	}
	
	public SimpleID(UUID uuid) {
		this.uuid = uuid;
		label = uuid.toString().substring(0, 4);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof SimpleID)) 
			return false;
		
		return uuid.equals(((SimpleID) obj).uuid);	
	}
	
	@Override
	public int hashCode() {
		return uuid.hashCode();
	}

	@Override
	public String label() {
		return label;
	}

	@Override
	public UUID uuid() {
		return uuid;
	}

	@Override
	public int compareTo(UniversalID o) {
		return uuid.compareTo(o.uuid());
	}
	
	@Override
	public String toString() {
		return label;
	}
}
