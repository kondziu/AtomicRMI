package put.atomicrmi.agents;

import put.atomicrmi.optsva.Transaction;

import java.lang.annotation.Annotation;

/**
 * Created by ksiek on 25.01.17.
 */
public interface Agent {

    public final int INF = Transaction.INF;

    public enum Trigger {ADD_GOAL, REMOVE_GOAL, ADD_BELIEF, REMOVE_BELIEF}

}
