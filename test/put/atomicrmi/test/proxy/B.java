package soa.atomicrmi.test.proxy;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.opt.TransactionalUnicastRemoteObject;

interface B extends Remote {
	public String getName() throws RemoteException;
}

class ImplB extends TransactionalUnicastRemoteObject implements B {
	private static final long serialVersionUID = 1L;

	protected ImplB() throws RemoteException {
		super();
	}

	public String getName() throws RemoteException {
		System.out.println("Here (from ImplB) " + this.getClass().getName());
		return "I'm ImplB!";
	}

}

class B2 implements Serializable {
	private static final long serialVersionUID = 549313246790420061L;

	public void foo() {
		System.out.println("Here (from B2)" + this.getClass().getName());
	}
}