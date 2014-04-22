//package examples.refcell;
package put.atomicrmi.test.refcell;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ReferrenceCellServer extends Remote {
	Cell getCell(Integer index) throws RemoteException;
}