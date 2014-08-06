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
	enum Mode {
		READ_ONLY, WRITE_ONLY, ANY
	}

	public Mode mode();
}
