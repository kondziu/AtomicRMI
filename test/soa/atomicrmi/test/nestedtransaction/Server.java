package soa.atomicrmi.test.nestedtransaction;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.TransactionsLock;

public class Server {
	public static void main(String[] args) throws RemoteException {
		// Parse the commandline arguments to figure out the hostname and port
		// of RMI registry.
		if (args.length < 2) {
			System.err.println("Required arguments: " + "host and port of RMI registry.");
			System.exit(1);
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);

		// Get a reference to RMI registry.
		Registry registry = LocateRegistry.getRegistry(host, port);

		// Initialize bank accounts.
		Account a = new AccountImpl(1000);
		Account b = new AccountImpl(1000);
		Account c = new AccountImpl(1000);
		Account d = new AccountImpl(1000);

		// Bind addresses.
		registry.rebind("A", a);
		registry.rebind("B", b);
		registry.rebind("C", c);
		registry.rebind("D", d);

		// Initialize synchronization mechanisms for transactions.
		TransactionsLock.initialize(registry);
	}
}
