package soa.atomicrmi.test.refcell;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.opt.Transaction;
import put.atomicrmi.opt.TransactionalUnicastRemoteObject;
import soa.atomicrmi.test.tools.User;
import soa.atomicrmi.test.tools.User.Choice;

public class ClientD extends TransactionalUnicastRemoteObject implements ReferrenceCellClient {

	private static final long serialVersionUID = -9220371995510605963L;

	protected ClientD() throws RemoteException {
		super();
	}

	public void callback(Cell cell) throws RemoteException {
		System.out.println("callback: " + cell.get());
	}

	public static void run(String host, int port, int publication, int subscription) throws RemoteException,
			NotBoundException {

		Registry registry = LocateRegistry.getRegistry(host, port);
		ReferrenceCellPubSubServer server = (ReferrenceCellPubSubServer) registry.lookup("ServerD");
		ReferrenceCellClient client = new ClientD();

		Transaction transaction = new Transaction();
		server = (ReferrenceCellPubSubServer) transaction.accesses(server, 2);

		System.out.println("Starting transaction at " + ClientD.class);
		User.waitUp();

		transaction.start();

		if (subscription != 0) {
			server.subscribe(client, subscription);
		}

		if (publication != 0) {
			Cell cell = new CellImpl(); // Create a fresh object
			cell.set(publication);
			server.publish(publication, cell); // Publish the object
		}

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
		int publication = 0, subscription = 0;

		if (args.length == 4) {
			host = args[0];
			port = Integer.valueOf(args[1]);
			publication = Integer.parseInt(args[2]);
			subscription = Integer.parseInt(args[3]);
		} else if (args.length == 3) {
			port = Integer.valueOf(args[0]);
			publication = Integer.parseInt(args[1]);
			subscription = Integer.parseInt(args[2]);
		} else if (args.length == 2) {
			publication = Integer.parseInt(args[0]);
			subscription = Integer.parseInt(args[1]);
		} else {
			System.out.println("Usage: java " + ClientD.class.getSimpleName()
					+ " [[host] port] publication subscription");
			System.exit(1);
		}

		try {
			run(host, port, publication, subscription);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}

		// System.exit(0);
	}
}