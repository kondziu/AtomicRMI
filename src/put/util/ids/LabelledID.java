package put.util.ids;

import java.util.UUID;

public class LabelledID implements UniversalID {

	private static final long serialVersionUID = 5484947689859957913L;
	
	private final String label;
	private final UUID uuid;

	public LabelledID(String label, UUID uuid) {
		this.label = label;
		this.uuid = uuid;
	}

	public LabelledID(String label) {
		this.label = label;
		this.uuid = UUID.randomUUID();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UniversalID)) 
			return false;
		
		return uuid.equals(((UniversalID) obj).uuid());	
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
