package put.atomicrmi.test.proxy;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.opt.Transaction;
import put.atomicrmi.opt.TransactionException;

public class ProxyTest {
	public void test() throws RemoteException, AlreadyBoundException, TransactionException, NotBoundException {
		Registry registry = LocateRegistry.getRegistry(1099);
		A a = (A) registry.lookup("A");

		System.out.println(a.getClass());
		System.out.println(a.getName());
		System.out.println(a.getB().getName());

		a.getB2().foo();

		Transaction t = new Transaction();
		A aa = (A) t.accesses(a);

		t.start();

		System.out.println(aa.getName());
		aa.test(aa);
	}

	public static void main(String[] args) throws RemoteException, AlreadyBoundException, NotBoundException,
			TransactionException {
		ProxyTest test = new ProxyTest();
		test.test();
	}
}
