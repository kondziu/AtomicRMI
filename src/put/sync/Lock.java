package put.sync;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Lock extends Remote {
	void lock(Object id) throws RemoteException;

	void release(Object id) throws RemoteException;

}
