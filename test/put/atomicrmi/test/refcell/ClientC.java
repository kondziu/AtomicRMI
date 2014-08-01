package put.atomicrmi.test.refcell;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionalUnicastRemoteObject;
import put.atomicrmi.test.tools.User;
import put.atomicrmi.test.tools.User.Choice;

public class ClientC extends TransactionalUnicastRemoteObject implements ReferrenceCellClient {

	private static final long serialVersionUID = -9220371995510605963L;

	private int value;

	protected ClientC(int value) throws RemoteException {
		super();
		this.value = value;
	}

	public void callback(Cell cell) throws RemoteException {
		System.out.println("before: " + cell.get());
		cell.set(value);
		System.out.println("after: " + cell.get());
	}

	public static void run(String host, int port, int value) throws RemoteException, NotBoundException {

		Registry registry = LocateRegistry.getRegistry(host, port);
		ReferrenceCellRequestServer server = (ReferrenceCellRequestServer) registry.lookup("ServerC");
		ReferrenceCellClient client = new ClientC(value);

		Transaction transaction = new Transaction();
		server = (ReferrenceCellRequestServer) transaction.accesses(server, 2);

		System.out.println("Starting transaction at " + ClientC.class);
		User.waitUp();

		transaction.start();

		server.requestCell(client);

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
			System.out.println("Usage: java " + ClientC.class.getSimpleName() + " [[host] port] v");
			System.exit(1);
		}

		try {
			run(host, port, value);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}

		// System.exit(0);
	}
}