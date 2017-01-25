package put.atomicrmi.agents;

import put.atomicrmi.optsva.Transaction;


/**
 * Created by ksiek on 24.01.17.
 */
public class AgentCarter implements Agent {
    @Triggers({
            @Event(type=Trigger.ADD_GOAL, term="G1"),
            @Event(type=Trigger.ADD_BELIEF, term="X"),
    })
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
    public void planA(Belief x, Belief y, Belief z, Goal g1) throws Exception {
        System.out.println("Carter executing plan \"planA\"");
        x.read();

        y.read();
        y.write(1);

        z.read();

        if(g1.isTrue()) {
            g1.setFalse();
        } else {
            g1.setTrue();
        }
        System.out.println("Carter done executing plan \"planA\"");
    }

    @Triggers({
            @Event(type=Trigger.ADD_GOAL, term="G2")
    })
    @Context({"X", "Y"})
    @Execution(
            beliefs={
                    @AccessBelief(belief="X", reads=0, writes=1),
                    @AccessBelief(belief="Y", reads=0, writes=1),
            },
            goals = {
                    @AccessGoal(goal="G2", reads=0, writes=1)
            }
    )
    public void planB(Belief x, Belief y, Goal g2) throws Exception {
        System.out.println("Carter executing plan \"planB\"");

        x.write(2);
        y.write(2);

        g2.setFalse();
        System.out.println("Carter done executing plan \"planB\"");
    }
}
