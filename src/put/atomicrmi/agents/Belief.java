package put.atomicrmi.agents;

import put.atomicrmi.optsva.Access;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by ksiek on 24.01.17.
 */
public interface Belief<T> extends Remote {
    @Access(Access.Mode.NONTRANSACTIONAL)
    String getID() throws RemoteException;

    @Access(Access.Mode.READ_ONLY)
    T read() throws RemoteException;

    @Access(Access.Mode.WRITE_ONLY)
    void write(T value) throws RemoteException;

    @Access(Access.Mode.READ_ONLY)
    boolean isTrue() throws RemoteException;

    @Access(Access.Mode.WRITE_ONLY)
    void setTrue() throws RemoteException;

    @Access(Access.Mode.WRITE_ONLY)
    void setFalse() throws RemoteException;
}