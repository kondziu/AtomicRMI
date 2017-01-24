package put.atomicrmi.agents;

import put.atomicrmi.optsva.Transaction;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by ksiek on 24.01.17.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessBelief {
    String belief();

    int reads();

    int writes();
}
