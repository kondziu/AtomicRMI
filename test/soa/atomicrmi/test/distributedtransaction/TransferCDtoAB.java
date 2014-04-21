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

public class TransferCDtoAB {
	public static void main(String[] args) throws NotBoundException, IOException {
		// Parse the commandline arguments to figure out the hostname and port
		// of RMI registry.
		if (args.length < 2) {
			System.err.println("Required arguments: " + "hostname and port of RMI registry "
					+ "and hostname and port of other RMI registry");
			System.exit(1);
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);

		// Get a reference to RMI registry.
		Registry registry = LocateRegistry.getRegistry(host, port);

		// Get references to remote objects.
		Account cd = (Account) registry.lookup("CD");
		Account ab = (Account) registry.lookup("AB");

		// Wait for the user.
		System.out.println("About to run transactional code.");
		User.waitUp();

		// Transaction header.
		Transaction transaction = new Transaction(registry);
		final Account tcd = (Account) transaction.accesses(cd, 3);
		final Account tab = (Account) transaction.accesses(ab, 3);

		transaction.start(new Transactable() {

			public void atomic(Transaction transaction) throws RemoteException {
				// Check balance on both accounts.
				System.out.println("Balance on AB: " + tab.getBalance());
				System.out.println("Balance on CD: " + tcd.getBalance());

				// Transfer funds from A to B.
				tcd.withdraw(1000);
				tab.deposit(1000);

				// Check balance again.
				System.out.println("New balance on AB: " + tab.getBalance());
				System.out.println("New balance on CD: " + tcd.getBalance());

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
