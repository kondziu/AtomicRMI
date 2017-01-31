package put.atomicrmi.agents;


/**
 * Created by ksiek on 24.01.17.
 */
public class AgentSmith implements Agent {
    @Triggers({
            @Event(type=Trigger.REMOVE_GOAL, term="G1")
    })
    @Context({"X", "Y", "Z"})
    @Execution(
            beliefs={
                @AccessBelief(belief="X", reads=1, writes=1),
                @AccessBelief(belief="Y", reads=1, writes=1),
            },
            goals = {
                @AccessGoal(goal="G2", reads=0, writes=1)
            }
    )
    public void planA(AgentSystem system, Belief x, Belief y, Goal g2) throws Exception {
        System.out.println("Smith executing plan \"planA\"");

        x.read();
        x.write(1);
        y.read();
        y.write(1);

        g2.setTrue();
        System.out.println("Smith done executing plan \"planA\"");
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
    public void planB(AgentSystem system, Belief x, Belief y, Goal g2) throws Exception {
        System.out.println("Smith executing plan \"planB\"");

        x.write(2);
        y.write(2);

        g2.setFalse();

        system.registerGoal("G3", 0, true);
        System.out.println("Smith done executing plan \"planB\"");
    }
}
