package soa.atomicrmi.test.rollback;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.RollbackForcedException;
import soa.atomicrmi.Transaction;

public class ClientB {
	public static void main(String[] args) throws AlreadyBoundException, NotBoundException, IOException {
		Registry registry = LocateRegistry.getRegistry(1099);

		Transaction t = new Transaction();
		A a = (A) t.accesses(registry.lookup("A"));

		System.out.println("start()");

		try {
			t.start();

			System.out.println("a.getName() = " + a.getName());

			System.out.print("Press enter to continue");
			System.in.read();

			a.setName("After B");

			System.out.print("Press enter to continue");
			System.in.read();

			System.out.println("a.getName() = " + a.getName());

			System.out.println("commit()");

			t.commit();
			System.out.println("finished");
		} catch (RollbackForcedException e) {
			System.out.println("rollback forced " + t.getState());
			System.out.println(e.getMessage());
		}
		System.out.println("a.getName() = " + a.getName());
	}
}
