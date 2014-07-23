package soa.atomicrmi.test.distributed;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.TransactionException;

public class Server {
	// private String host;
	private int port;

	public Server(String host, int port) {
		// this.host = host;
		this.port = port;
	}

	public void bind() throws RemoteException, AlreadyBoundException, TransactionException {

		A a = new ImplA();
		a.setName("Before");

		Registry registry = LocateRegistry.createRegistry(port);
		registry.bind("A", a);

		//TransactionsLock.initialize(registry);
		// System.out.println("A: " + a.getName());
	}

	public static void main(String[] args) throws RemoteException, AlreadyBoundException, NotBoundException,
			TransactionException {
		if (args.length < 2) {
			System.err.println("Required arguments: " + "hostname and port of RMI registry.");
		}

		// Parse commandline arguments.
		String host = args[0];
		int port = Integer.parseInt(args[1]);

		// Put shared remote objects in the register.
		Server server = new Server(host, port);
		server.bind();
	}
}
