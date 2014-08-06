package put.atomicrmi;

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
	 * Shared remote object or shared remote object method access mode.
	 * 
	 * <P>
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
	enum Mode {
		READ_ONLY, WRITE_ONLY, ANY
	}

	/**
	 * Shared object or method access mode. See {@link Mode}.
	 * 
	 * @return object/method access mode
	 */
	public Mode mode();
}
