package put.atomicrmi.test.basic;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;

import put.atomicrmi.opt.Transaction;
import put.atomicrmi.opt.TransactionException;

public class Basic {

	public void bind() throws RemoteException, AlreadyBoundException, TransactionException {
		A a = new ImplA();
		a.setName("Object A");

		System.out.println(((UnicastRemoteObject) a).getRef().remoteToString());

		Registry registry = LocateRegistry.createRegistry(1099);
		registry.bind("A", a);

		System.out.println(((UnicastRemoteObject) a).getRef().remoteToString());

		Transaction t = new Transaction();
		t.accesses(a);
	}

	public void test() throws RemoteException, NotBoundException, TransactionException {
		Registry registry = LocateRegistry.getRegistry(1099);
		A a = (A) registry.lookup("A");
		System.out.println(a.getName());
		System.out.println(a.getB().getName());

		a.getB2().foo();

		// java.lang.reflect.Proxy p = (java.lang.reflect.Proxy) a;

		RemoteObject ro = (RemoteObject) java.lang.reflect.Proxy.getInvocationHandler(a);

		System.out.println(ro.getRef());
		System.out.println(((RemoteObject) java.lang.reflect.Proxy.getInvocationHandler(registry.lookup("A"))).getRef()
				.remoteToString());
		System.out.println(((RemoteObject) java.lang.reflect.Proxy.getInvocationHandler(registry.lookup("A"))).getRef()
				.remoteToString());
		System.out.println(((RemoteObject) java.lang.reflect.Proxy.getInvocationHandler(((A) registry.lookup("A"))
				.getB())).getRef().remoteToString());
		System.out.println(((RemoteObject) java.lang.reflect.Proxy.getInvocationHandler(((A) registry.lookup("A"))
				.getB())).getRef().remoteToString());

		// RemoteStub p = (RemoteStub) a;

		// for (Interface m : a.getClass().getInterfaces())
		// System.out.println(m.getName());

		// Class<? extends A> ca = a.getClass();
		System.out.println(a.getClass().getName());
		for (Class<?> c : a.getClass().getInterfaces())
			System.out.println(c);

		Transaction t = new Transaction();
		t.accesses(a);
	}

	public static void main(String[] args) throws RemoteException, AlreadyBoundException, NotBoundException,
			TransactionException {
		Basic s = new Basic();
		s.bind();
		s.test();
	}

}
