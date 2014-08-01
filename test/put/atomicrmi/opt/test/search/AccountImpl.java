package put.atomicrmi.opt.test.search;

import java.rmi.RemoteException;

import put.atomicrmi.TransactionalUnicastRemoteObject;
import put.atomicrmi.ops.Read;

public class AccountImpl extends TransactionalUnicastRemoteObject implements Account {

	private static final long serialVersionUID = 5553833002385560844L;

	int balance;

	private String name;

	protected AccountImpl(String name, int balance) throws RemoteException {
		super();
		this.name = name;
		this.balance = balance;
	}

	public void deposit(int sum) throws RemoteException {
		balance += sum;
		System.out.println(name + ": " + "deposit: " + sum + " balance: " + balance);
	}

	public void withdraw(int sum) throws RemoteException {
		balance -= sum;
		System.out.println(name + ": " + "withdraw: " + sum + " balance: " + balance);

	}

	public int getBalance() throws RemoteException {
		System.out.println(name + ": " + "balance: " + balance);
		return balance;
	}
	
	@Override
	@Read
	public String getName() throws RemoteException {
		return name;		
	}
}
