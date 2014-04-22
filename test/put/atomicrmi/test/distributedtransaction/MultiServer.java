package put.atomicrmi.test.distributedtransaction;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class MultiServer {
	public static void main(String[] args) throws RemoteException, NotBoundException {
		// Parse the commandline arguments to figure out the hostname and port
		// of RMI registry.
		if (args.length < 4) {
			System.err.println("Required arguments: " + "hostname and port of RMI registry "
					+ "and hostname and port of other RMI registry");
			System.exit(1);
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);

		String singleHost = args[2];
		int singlePort = Integer.parseInt(args[3]);

		// Get a reference to RMI registry.
		Registry registry = LocateRegistry.getRegistry(host, port);
		Registry otherRegistry = LocateRegistry.getRegistry(singleHost, singlePort);

		Account[] m1components = { (Account) otherRegistry.lookup("A"), (Account) otherRegistry.lookup("B"),
				(Account) otherRegistry.lookup("C"), };

		Account[] m2components = { (Account) otherRegistry.lookup("B"), (Account) otherRegistry.lookup("C"),
				(Account) otherRegistry.lookup("D"), };

		Account[] abComponents = { (Account) otherRegistry.lookup("A"), (Account) otherRegistry.lookup("B"), };

		Account[] cdComponents = { (Account) otherRegistry.lookup("C"), (Account) otherRegistry.lookup("D"), };

		// Initialize bank accounts.
		Account m1 = new MultiAccountImpl(m1components);
		Account m2 = new MultiAccountImpl(m2components);
		Account ab = new MultiAccountImpl(abComponents);
		Account cd = new MultiAccountImpl(cdComponents);

		// Bind addresses.
		registry.rebind("M1", m1);
		registry.rebind("M2", m2);
		registry.rebind("AB", ab);
		registry.rebind("CD", cd);

		// Initialize synchronization mechanisms for transactions.
		// XXX rem TransactionsLock.initialize(registry);
	}
}
