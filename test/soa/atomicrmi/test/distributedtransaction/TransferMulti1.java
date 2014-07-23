package soa.atomicrmi.test.distributedtransaction;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.Transactable;
import soa.atomicrmi.Transaction;
import soa.atomicrmi.test.tools.User;
import soa.atomicrmi.test.tools.User.Choice;

public class TransferMulti1 {
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
		Account m1 = (Account) registry.lookup("M1");
		Account d = (Account) singleRegistry.lookup("D");

		// Wait for the user.
		System.out.println("About to run transactional code.");
		User.waitUp();

		// Transaction header.
		Transaction transaction = new Transaction();
		final Account tm1 = (Account) transaction.accesses(m1, 3);
		final Account td = (Account) transaction.accesses(d, 3);

		transaction.start(new Transactable() {

			public void atomic(Transaction transaction) throws RemoteException {
				// Check balance on both accounts.
				System.out.println("Balance on M1: " + tm1.getBalance());
				System.out.println("Balance on  D: " + td.getBalance());

				// Transfer funds from A to B.
				tm1.withdraw(600);
				td.deposit(600);

				// Check balance again.
				System.out.println("New balance on M1: " + tm1.getBalance());
				System.out.println("New balance on  D: " + td.getBalance());

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
