package put.atomicrmi.test.distributedtransaction;

import java.rmi.RemoteException;

import put.atomicrmi.TransactionalUnicastRemoteObject;

public class MultiAccountImpl extends TransactionalUnicastRemoteObject implements Account {

	private static final long serialVersionUID = 5553833002385560844L;
	private Account[] accounts;

	protected MultiAccountImpl(Account[] accounts) throws RemoteException {
		super();
		this.accounts = accounts;
	}

	public void deposit(int sum) throws RemoteException {
		for (Account account : accounts) {
			account.deposit(sum / accounts.length);
		}
	}

	public void withdraw(int sum) throws RemoteException {
		for (Account account : accounts) {
			account.withdraw(sum / accounts.length);
		}
	}

	public int getBalance() throws RemoteException {
		int balance = 0;
		for (Account account : accounts) {
			balance += account.getBalance();
		}
		return balance;
	}
}
