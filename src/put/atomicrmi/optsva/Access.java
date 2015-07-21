package put.atomicrmi.optsva;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Specifies whether a class or a method accesses data in read-only, write-only,
 * or read-write mode.
 * 
 * @author Konrad Siek
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Access {
	/**
	 * Method access mode.
	 * 
	 * <p>
	 * READ_ONLY mode means objects can only be read, WRITE_ONLY mode means
	 * objects can only be written to, and ANY mode means that objects can be
	 * either read from or written to, or accessed in other ways.
	 * 
	 * <p>
	 * Writing to an object means using methods annotated as writes, reading
	 * from an object means using methods annotated as reads. Only objects in
	 * ANY mode can use methods which are neither reads or writes.
	 * 
	 * @author Konrad Siek
	 */
	public enum Mode {
		READ_ONLY, WRITE_ONLY, ANY
	}

	/**
	 * Shared object method access mode. See {@link Mode}.
	 * 
	 * @return method access mode
	 */
	public Mode value();
}
