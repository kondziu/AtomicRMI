package put.atomicrmi.agents;


/**
 * Created by ksiek on 24.01.17.
 */
public class AgentSmith implements Agent {
    @Triggers({
            @Event(type=Trigger.ADD_GOAL, term="G1")
    })
    @Context({"X", "Y", "Z"})
    @Execution(
            beliefs={
                @AccessBelief(belief="X", reads=1, writes=1),
                @AccessBelief(belief="Y", reads=1, writes=1),
            },
            goals = {
                @AccessGoal(goal="G1", reads=1, writes=1)
            }
    )
    public void plan(Belief x, Belief y, Goal g1) throws Exception {
        System.out.println("Executing plan \"plan\"");
        x.read();
        x.write(1);

        y.read();
        y.write(1);

        if(g1.isTrue()) {
            g1.setFalse();
        } else {
            g1.setTrue();
        }
        System.out.println("Done executing plan \"plan\"");
    }

    public void notAPLan() {

    }
}
