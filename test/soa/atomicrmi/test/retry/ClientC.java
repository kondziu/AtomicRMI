package soa.atomicrmi.test.retry;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.Transactable;
import soa.atomicrmi.Transaction;

public class ClientC {
	public static void main(String[] args) throws AlreadyBoundException, NotBoundException, IOException {
		Registry registry = LocateRegistry.getRegistry(1099);

		Transaction t = new Transaction(registry);
		final A a = (A) t.accesses(registry.lookup("A"));

		t.start(new Transactable() {
			public void atomic(Transaction t) throws RemoteException {
				try {
					System.out.println("start()");
					System.out.println("a.getName() = " + a.getName());

					if (!"After B".equals(a.getName()))
						t.retry();

					System.out.print("Press enter to continue");
					System.in.read();

					a.setName("After C");

					System.out.print("Press enter to continue");
					System.in.read();

					System.out.println("a.getName() = " + a.getName());

					System.out.println("commit()");

					t.commit();
					System.out.println("finished");
				} catch (IOException e) {
					if (e instanceof RemoteException)
						throw (RemoteException) e;
				}
			}
		});
	}
}
