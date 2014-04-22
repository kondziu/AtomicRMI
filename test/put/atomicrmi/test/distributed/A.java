package put.atomicrmi.test.distributed;

import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.opt.TransactionalUnicastRemoteObject;

interface PreA extends Remote {
}

interface A extends Remote {
	public String getValue() throws RemoteException;

	public void setName(String name) throws RemoteException;
}

class ImplA extends TransactionalUnicastRemoteObject implements A {
	private static final long serialVersionUID = 1L;

	private String name;

	protected ImplA() throws RemoteException {
		super();
	}

	public String getValue() throws RemoteException {
		return name;
	}

	public void setName(String name) throws RemoteException {
		this.name = name;
		System.out.println("Remote object value: " + name);
	}
}