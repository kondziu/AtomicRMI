package put.atomicrmi.test.distributedtransaction;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.Transaction;
import put.atomicrmi.Transactional;
import put.atomicrmi.test.tools.User;
import put.atomicrmi.test.tools.User.Choice;

public class TransferMulti2 {
	public static void main(String[] args) throws NotBoundException, IOException {
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
		Registry singleRegistry = LocateRegistry.getRegistry(singleHost, singlePort);

		// Get references to remote objects.
		Account m2 = (Account) registry.lookup("M2");
		Account a = (Account) singleRegistry.lookup("A");

		// Wait for the user.
		System.out.println("About to run transactional code.");
		User.waitUp();

		// Transaction header.
		Transaction transaction = new Transaction();
		final Account tm2 = (Account) transaction.accesses(m2, 3);
		final Account ta = (Account) transaction.accesses(a, 3);

		transaction.start(new Transactional() {

			public void atomic(Transaction transaction) throws RemoteException {
				// Check balance on both accounts.
				System.out.println("Balance on M2: " + tm2.getBalance());
				System.out.println("Balance on  A: " + ta.getBalance());

				// Transfer funds from A to B.
				tm2.withdraw(1200);
				ta.deposit(1200);

				// Check balance again.
				System.out.println("New balance on M2: " + tm2.getBalance());
				System.out.println("New balance on  A: " + ta.getBalance());

				// User chooses how to finish the transaction: COMMIT, ROLLBACK
				// or RETRY.
				Choice choice = User.selectAnyEnding();
				switch (choice) {
				case COMMIT:
					System.out.println("Committing.");
					transaction.commit();
					break;
				case ROLLBACK:
					System.out.println("Rolling back.");
					transaction.rollback();
					break;
				case RETRY:
					System.out.println("Retrying.");
					transaction.retry();
					break;
				}
			}

		});

		System.exit(0);
	}
}
