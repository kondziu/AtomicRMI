package soa.atomicrmi.test.retryexample;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface A extends Remote {
	public int getValue() throws RemoteException;

	public void setValue(int value) throws RemoteException;
}
