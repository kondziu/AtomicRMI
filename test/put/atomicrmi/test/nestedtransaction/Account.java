package put.atomicrmi.test.nestedtransaction;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Account extends Remote {
	public void deposit(int sum) throws RemoteException;

	public void withdraw(int sum) throws RemoteException;

	public int getBalance() throws RemoteException;

	public int callSelf() throws RemoteException;
}
