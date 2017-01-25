package put.atomicrmi.agents;

import put.atomicrmi.optsva.objects.TransactionalUnicastRemoteObject;

import java.io.Serializable;
import java.rmi.RemoteException;

/**
 * Created by ksiek on 24.01.17.
 */
public class GoalImpl<T> extends TransactionalUnicastRemoteObject implements Goal<T>, Cloneable {
    private final String id;
    private T value;
    private boolean predicate;

    protected GoalImpl(String id, T value, boolean initiallyTrue) throws RemoteException {
        this.value = value;
        this.predicate = initiallyTrue;
        this.id = id;
    }

    @Override
    public String getID() throws RemoteException { return id; }

    @Override
    public T read() {
        return value;
    }

    @Override
    public void write(T value) {
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
