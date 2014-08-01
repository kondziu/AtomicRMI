package put.unit.vars;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Variable extends Remote {

	int read() throws RemoteException;

	void write(int v) throws RemoteException;

}
