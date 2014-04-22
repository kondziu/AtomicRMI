package put.atomicrmi.test.nestedtransaction;

import java.rmi.RemoteException;

import put.atomicrmi.opt.TransactionalUnicastRemoteObject;

public class AccountImpl extends TransactionalUnicastRemoteObject implements Account {

	private static final long serialVersionUID = 5553833002385560844L;

	int balance;

	protected AccountImpl(int balance) throws RemoteException {
		super();
		this.balance = balance;
	}

	public void deposit(int sum) throws RemoteException {
		balance += sum;
	}

	public void withdraw(int sum) throws RemoteException {
		balance -= sum;
	}

	public int getBalance() throws RemoteException {
		return balance;
	}

	public int callSelf() throws RemoteException {
		return this.getBalance();
	}
}
