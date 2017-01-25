package put.atomicrmi.agents;

import java.lang.annotation.Annotation;

/**
 * Created by ksiek on 25.01.17.
 */
public class TriggeringEvent implements Event {
    private Agent.Trigger type;
    private String term;

    public TriggeringEvent(Agent.Trigger type, String term) {
        this.type = type;
        this.term = term;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Event.class;
    }

    @Override
    public Agent.Trigger type() {
        return type;
    }

    @Override
    public String term() {
        return term;
    }

    @Override
    public int hashCode() {
        return type.hashCode() + term.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Event))
            return false;
        Event te = (Event) obj;
        return term.equals(te.term()) && type.equals(te.type());
    }
}
