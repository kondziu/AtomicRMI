package put.atomicrmi.opt.test.bank;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.opt.Transaction;
import put.atomicrmi.test.tools.User;
import put.atomicrmi.test.tools.User.Choice;

public class Transfer {
	public static void main(String[] args) throws NotBoundException, IOException {
		// Parse the commandline arguments to figure out the hostname and port
		// of RMI registry.
		if (args.length < 2) {
			System.err.println("Required arguments: " + "hostname and port of RMI registry.");
			System.exit(1);
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);

		// Get a reference to RMI registry.
		Registry registry = LocateRegistry.getRegistry(host, port);

		// Get references to remote objects.
		Account a = (Account) registry.lookup("A");
		Account b = (Account) registry.lookup("B");

		// Wait for the user.
		System.out.println("About to run transactional code.");
		User.waitUp();

		// Transaction header.
		Transaction transaction = new Transaction();
		Account ta = (Account) transaction.accesses(a, 3);
		Account tb = (Account) transaction.accesses(b, 3);

		transaction.start();

		// Check balance on both accounts.
		System.out.println("Balance on A: " + ta.getBalance());
		System.out.println("Balance on B: " + tb.getBalance());

		// Transfer funds from A to B.
		System.out.println("Transfering 100 from A to B.");
		ta.withdraw(100);
		tb.deposit(100);

		// Check balance again.
		System.out.println("New balance on A: " + ta.getBalance());
		System.out.println("New balance on B: " + tb.getBalance());

		// User chooses how to finish the transaction: COMMIT or ROLLBACK.
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

		System.exit(0);
	}
}
