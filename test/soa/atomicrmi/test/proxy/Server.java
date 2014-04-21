package soa.atomicrmi.test.proxy;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import net.sf.cglib.proxy.Enhancer;
import soa.atomicrmi.Transaction;
import soa.atomicrmi.TransactionException;

public class Server {
	public void bind() throws RemoteException, AlreadyBoundException, TransactionException {
		A a = new ImplA();
		a.setName("Object A");

		System.out.println(((UnicastRemoteObject) a).getRef().remoteToString());

		Registry registry = LocateRegistry.createRegistry(1099);
		registry.bind("A", a);

		System.out.println(((UnicastRemoteObject) a).getRef().remoteToString());

		Transaction t = new Transaction(registry);
		A aa = (A) t.accesses(a);

		System.out.println(Enhancer.isEnhanced(aa.getClass()));
	}

	public static void main(String[] args) throws RemoteException, AlreadyBoundException, NotBoundException,
			TransactionException {
		Server server = new Server();
		server.bind();
	}
}
