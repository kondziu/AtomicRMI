package put.atomicrmi.agents;

import put.atomicrmi.optsva.objects.TransactionalUnicastRemoteObject;

import java.io.Closeable;
import java.io.Serializable;
import java.rmi.RemoteException;

/**
 * Created by ksiek on 24.01.17.
 */
public class BeliefImpl extends TransactionalUnicastRemoteObject implements Belief, Serializable, Cloneable {
    private final String id;
    private String value;
    private boolean predicate;

    protected BeliefImpl(String id, String value, boolean initiallyTrue) throws RemoteException {
        this.value = value;
        this.predicate = initiallyTrue;
        this.id = id;
    }

    @Override
    public String getID() throws RemoteException { return id; }

    @Override
    public String read() {
        return value;
    }

    @Override
    public void write(String value) {
        this.value = value;
    }

    @Override
    public boolean isTrue() {
        return predicate;
    }

    @Override
    public void setTrue() {
        predicate = true;
    }

    @Override
    public void setFalse() {
        predicate = false;
    }


}
