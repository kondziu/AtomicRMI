package put.atomicrmi.test.distributed;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.opt.Transactional;
import put.atomicrmi.opt.Transaction;
import put.atomicrmi.test.distributed.Tools.Ending;

public class Client {
	private A object;
	private Registry registry;

	public Client(Registry registry, A object) {
		this.object = object;
		this.registry = registry;
	}

	public static void main(String[] args) throws AlreadyBoundException, NotBoundException, IOException,
			InterruptedException {
		if (args.length < 2) {
			System.err.println("Required arguments: " + "hostname and port of RMI registry.");
			System.err.println("Optional 3rd argument: " + "result for T1: COMMIT, ROLLBACK, or RETRY.");
		}

		// Parse commandline arguments.
		String host = args[0];
		int port = Integer.parseInt(args[1]);

		// The way T1 terminates may be optionally specified up front by a
		// commandline argument.
		Ending ending = args.length >= 3 ? Ending.valueOf(args[2]) : null;

		// Look up the register and the shared remote object.
		Registry registry = LocateRegistry.getRegistry(host, port);
		A object = (A) registry.lookup("A");

		Client client = new Client(registry, object);

		// Execute transactions
		client.executeReading("T0");

		// while (client.executeWritingOld("T1", ending) == Ending.RETRY)
		// ;
		client.executeWriting("T1", ending);

		client.executeReading("T2");

		System.exit(0);
	}

	private void executeWriting(final String name, final Ending ending) throws IOException {
		Transaction transaction = new Transaction();
		final A object = transaction.accesses(this.object, 1);

		transaction.start(new Transactional() {

			public void atomic(Transaction t) throws RemoteException {
				System.out.println(name + " started.");

				object.setName("After");
				System.out.println(name + " wrote to A.");

				Ending e = ending;
				while (e == null) {
					try {
						e = Tools.pickEnding();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}

				System.out.println(name + " will " + e + ".");
				switch (e) {
				case COMMIT:
					t.commit();
					break;
				case ROLLBACK:
					t.rollback();
					break;
				case RETRY:
					t.retry();
					break;
				}
				System.out.println(name + " ended.");
			}
		});
	}

	@SuppressWarnings("unused")
	private Ending executeWritingOld(String name, Ending ending) throws IOException {
		Transaction transaction = new Transaction();
		A object = (A) transaction.accesses(this.object, 1);

		transaction.start();
		System.out.println(name + " started.");

		object.setName("After");
		System.out.println(name + " wrote to A.");

		while (ending == null) {
			ending = Tools.pickEnding();
		}

		System.out.println(name + " will " + ending + ".");
		switch (ending) {
		case COMMIT:
			transaction.commit();
			break;
		case ROLLBACK:
		case RETRY:
			transaction.rollback();
			break;
		}
		System.out.println(name + " ended.");

		return ending;
	}

	private void executeReading(String name) throws RemoteException {
		Transaction transaction = new Transaction();
		A object = (A) transaction.accesses(this.object, 1);

		transaction.start();
		System.out.println(name + " started.");

		String value = object.getValue();
		System.out.println(name + " read A: " + value + ".");

		transaction.commit();
		System.out.println(name + " ended.");
	}

}
