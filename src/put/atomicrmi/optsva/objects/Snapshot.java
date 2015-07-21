package put.atomicrmi.optsva.objects;

/**
 * Stores snapshot of particular remote object together with snapshot
 * version information.
 * 
 * @author Wojciech Mruczkiewicz
 */
class Snapshot {

	/**
	 * The binary representation of remote object.
	 */
	private byte[] image;

	/**
	 * Version information that determines when the snapshot was taken.
	 */
	private long rv;

	/**
	 * Constructs the snapshot with given version and object's image.
	 * 
	 * @param image
	 *            a serialized remote object.
	 * @param readVersion
	 *            version when the serialization occurred.
	 */
	Snapshot(byte[] image, long readVersion) {
		this.image = image;
		rv = readVersion;
	}

	/**
	 * Gives the serialized image reference.
	 * 
	 * @return the serialized image.
	 */
	byte[] getImage() {
		return image;
	}

	/**
	 * Gives the version of an image when the serialization occurred.
	 * 
	 * @return image version.
	 */
	long getReadVersion() {
		return rv;
	}
}