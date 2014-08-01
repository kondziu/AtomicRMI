package put.atomicrmi.test.basic;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.TransactionalUnicastRemoteObject;

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

	private static final long serialVersionUID = 7042724976177402270L;

	public void foo() {
		System.out.println("Here (from B2)" + this.getClass().getName());
	}
}