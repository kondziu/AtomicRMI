package put.atomicrmi.test.refcell;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.Transaction;
import put.atomicrmi.test.tools.User;
import put.atomicrmi.test.tools.User.Choice;

public class ClientB {

	public static void run(String host, int port, int value) throws RemoteException, NotBoundException {

		Registry registry = LocateRegistry.getRegistry(host, port);
		ReferrenceCellServer server = (ReferrenceCellServer) registry.lookup("ServerB");

		Transaction transaction = new Transaction();
		server = (ReferrenceCellServer) transaction.accesses(server, 2);

		System.out.println("Starting transaction at " + ClientB.class);
		User.waitUp();

		transaction.start();

		Cell cell1 = server.getCell(1);
		System.out.println("cell1 before: " + cell1.get());
		cell1.set(value);
		System.out.println("cell1 after: " + cell1.get());

		User.waitUp();

		Cell cell2 = server.getCell(2);
		System.out.println("cell2 before: " + cell2.get());
		cell2.set(value);
		System.out.println("cell2 after: " + cell2.get());

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
	}

	public static void main(String args[]) {

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
			System.out.println("Usage: java " + ClientB.class.getSimpleName() + " [[host] port] v");
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