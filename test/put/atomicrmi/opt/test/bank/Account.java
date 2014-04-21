package soa.atomicrmi.opt.test.bank;

import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.opt.ops.Read;

public interface Account extends Remote {
	public void deposit(int sum) throws RemoteException;

	public void withdraw(int sum) throws RemoteException;

	@Read
	public int getBalance() throws RemoteException;
}
