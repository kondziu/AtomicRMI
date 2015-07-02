package put.util.ids;

import java.util.UUID;

public class HumanReadableID implements UniversalID {

	private static final long serialVersionUID = 5484947689859957913L;

	private final String id;
	private final UUID uuid;

	public HumanReadableID(String label, UUID uuid) {
		this.id = label;
		this.uuid = uuid;
	}

	public HumanReadableID(String label) {
		this.id = label;
		this.uuid = UUID.randomUUID();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof HumanReadableID))
			return false;

		HumanReadableID huid = (HumanReadableID) obj;
		return id.equals(huid.id) && uuid.equals(huid.uuid);
	}

	@Override
	public int hashCode() {
		return id.hashCode() + uuid.hashCode();
	}

	@Override
	public String label() {
		return id;
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
		return id;
	}
}
