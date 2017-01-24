package put.atomicrmi.agents;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by ksiek on 24.01.17.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessGoal {
    String goal();

    int reads();

    int writes();
}
