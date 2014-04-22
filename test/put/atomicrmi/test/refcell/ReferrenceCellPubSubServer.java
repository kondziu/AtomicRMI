package put.atomicrmi.test.refcell;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ReferrenceCellPubSubServer extends Remote {
	void subscribe(ReferrenceCellClient cell, Integer key) throws RemoteException;

	void publish(Integer key, Cell cell) throws RemoteException;
}
