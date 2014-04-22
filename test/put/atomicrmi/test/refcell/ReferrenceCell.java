//package examples.refcell;
package put.atomicrmi.test.refcell;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ReferrenceCell extends Remote {
	void set(int v) throws RemoteException;

	int get() throws RemoteException;
}