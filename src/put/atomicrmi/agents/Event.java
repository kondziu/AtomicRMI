package put.atomicrmi.agents;

/**
 * Created by ksiek on 25.01.17.
 */
public @interface Event {
    AgentCarter.Trigger type();

    String term();
}
