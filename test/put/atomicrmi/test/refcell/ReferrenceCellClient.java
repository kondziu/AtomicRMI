//package examples.refcell;
package soa.atomicrmi.test.refcell;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ReferrenceCellClient extends Remote {
	void callback(Cell cell) throws RemoteException;
}