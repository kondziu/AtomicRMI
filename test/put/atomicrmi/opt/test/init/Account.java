package put.atomicrmi.opt.test.init;

import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.opt.ops.Read;
import put.atomicrmi.opt.ops.Write;

public interface Account extends Remote {
	@Write
	public void deposit(int sum) throws RemoteException;

	@Write
	public void withdraw(int sum) throws RemoteException;

	@Read
	public int getBalance() throws RemoteException;
}
