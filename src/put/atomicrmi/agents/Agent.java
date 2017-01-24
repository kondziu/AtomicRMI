package put.atomicrmi.agents;

import put.atomicrmi.optsva.Transaction;

/**
 * Created by ksiek on 24.01.17.
 */
public class Agent {

    private static final int INF = Transaction.INF;

    enum Trigger {ADD_GOAL, REMOVE_GOAL, ADD_BELIEF, REMOVE_BELIEF}
    // PLAN: head(triggerring event, context=beliefs) -> body(goals and actions)

    @Event(Trigger.ADD_BELIEF)
    @Context({"X", "Y", "Z"})
    @Execution(
            beliefs={
                @AccessBelief(belief="X", reads=1, writes=0),
                @AccessBelief(belief="Y", reads=1, writes=1),
                @AccessBelief(belief="Z", reads=INF, writes=INF),
            },
            goals = {
                @AccessGoal(goal="G1", reads=1, writes=1)
            }
    )
    public void plan(Belief x, Belief y, Belief z, Goal g1) {

    }
}
