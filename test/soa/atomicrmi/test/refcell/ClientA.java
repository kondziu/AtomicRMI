package soa.atomicrmi.test.refcell;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.Transaction;
import soa.atomicrmi.test.tools.User;
import soa.atomicrmi.test.tools.User.Choice;

public class ClientA {

	public static void run(String host, int port, int value) throws RemoteException, NotBoundException {

		Registry registry = LocateRegistry.getRegistry(host, port);
		ReferrenceCell cell = (ReferrenceCell) registry.lookup("ServerA");

		Transaction transaction = new Transaction(registry);
		cell = (ReferrenceCell) transaction.accesses(cell, 3);

		System.out.println("Starting transaction at " + ClientA.class);
		User.waitUp();

		transaction.start();

		System.out.println("before: " + cell.get());
		cell.set(value);
		System.out.println("after: " + cell.get());

		Choice choice = User.selectEnding();
		switch (choice) {
		case COMMIT:
			System.out.println("Committing.");
			transaction.commit();
			break;
		case ROLLBACK:
			System.out.println("Rolling back.");
			transaction.rollback();
			break;
		}

		System.out.println("Done.");
	}

	public static void main(String args[]) throws Exception {

		// default values for host, port, and number
		String host = "localhost";
		int port = 1099;
		int value = 0;

		if (args.length == 3) {
			host = args[0];
			port = Integer.valueOf(args[1]);
			value = Integer.parseInt(args[2]);
		} else if (args.length == 2) {
			port = Integer.valueOf(args[0]);
			value = Integer.parseInt(args[1]);
		} else if (args.length == 1)
			value = Integer.parseInt(args[0]);
		else {
			System.out.println("Usage: java " + ClientA.class.getSimpleName() + " [[host] port] v");
			System.exit(1);
		}

		try {
			run(host, port, value);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}

		System.exit(0);
	}
}