package soa.atomicrmi.test.transaction;

import java.rmi.Remote;
import java.rmi.RemoteException;

import soa.atomicrmi.TransactionalUnicastRemoteObject;

interface A extends Remote {
	public String getName() throws RemoteException;

	public void setName(String name) throws RemoteException;
}

class ImplA extends TransactionalUnicastRemoteObject implements A {
	private static final long serialVersionUID = 1L;

	private String name;

	protected ImplA() throws RemoteException {
		super();
	}

	public String getName() throws RemoteException {
		return name;
	}

	public void setName(String name) throws RemoteException {
		this.name = name;
	}
}