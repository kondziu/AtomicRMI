package soa.atomicrmi.test.refcell;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ReferrenceCellRequestServer extends Remote {
	void requestCell(ReferrenceCellClient client) throws RemoteException;
}
