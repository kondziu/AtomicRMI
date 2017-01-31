package put.atomicrmi.agents;

import put.atomicrmi.optsva.Access;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by ksiek on 24.01.17.
 */
public interface Goal extends Remote {
    @Access(Access.Mode.NONTRANSACTIONAL)
    String getID() throws RemoteException;

    @Access(Access.Mode.READ_ONLY)
    String read() throws RemoteException;

    @Access(Access.Mode.WRITE_ONLY)
    void write(String value) throws RemoteException;

    @Access(Access.Mode.READ_ONLY)
    boolean isTrue() throws RemoteException;

    @Access(Access.Mode.WRITE_ONLY)
    void setTrue() throws RemoteException;

    @Access(Access.Mode.WRITE_ONLY)
    void setFalse() throws RemoteException;
}