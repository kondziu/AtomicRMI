package put.atomicrmi.test.nestedtransaction;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.Transaction;
import put.atomicrmi.Transactional;
import put.atomicrmi.test.tools.User;
import put.atomicrmi.test.tools.User.Choice;

public class NestedTransfer {
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
		final Registry registry = LocateRegistry.getRegistry(host, port);

		// Get references to remote objects.
		final Account a = (Account) registry.lookup("A");
		final Account b = (Account) registry.lookup("B");
		final Account c = (Account) registry.lookup("C");
		final Account d = (Account) registry.lookup("D");

		// Wait for the user.
		System.out.println("About to run transactional code " + "for outer transaction.");
		User.waitUp();

		// Transaction header.
		Transaction transaction = new Transaction();
		final Account ta = (Account) transaction.accesses(a, 3);
		final Account tb = (Account) transaction.accesses(b, 3);
		final Account tc = (Account) transaction.accesses(c, 3);
		final Account td = (Account) transaction.accesses(d, 3);

		transaction.start(new Transactional() {

			public void atomic(Transaction transaction) throws RemoteException {
				// Check balance on both accounts.
				System.out.println("Balance on A: " + ta.getBalance());
				System.out.println("Balance on B: " + tb.getBalance());

				System.out.println("About to run transactional code " + "for inner transaction.");

				Transaction inner = new Transaction();
				final Account tc = (Account) inner.accesses(c, 3);
				final Account td = (Account) inner.accesses(d, 3);

				inner.start();
				System.out.println("Balance on C: " + tc.getBalance());
				System.out.println("Balance on D: " + td.getBalance());

				tc.withdraw(500);
				td.deposit(500);

				System.out.println("New balance on A: " + ta.getBalance());
				System.out.println("New balance on B: " + tb.getBalance());

				if (User.selectEnding() == Choice.ROLLBACK) {
					System.out.println("Rolling back inner.");
					inner.rollback();
				} else {
					System.out.println("Committing inner.");
					inner.commit();
				}

				// Transfer funds from A to B.
				ta.withdraw(1000);
				tb.deposit(1000);

				// Check balance again.
				System.out.println("New balance on A: " + ta.getBalance());
				System.out.println("New balance on B: " + tb.getBalance());

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