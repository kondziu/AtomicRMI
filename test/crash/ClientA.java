package crash;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.Transaction;

public class ClientA {
	public static void main(String[] args) throws AlreadyBoundException, NotBoundException, IOException,
			InterruptedException {
		Registry registry = LocateRegistry.getRegistry(1099);

		Transaction t = new Transaction(registry);
		A a = (A) t.accesses(registry.lookup("A"), 3);

		System.out.println("start()");

		t.start();

		System.out.println("a.getName() = " + a.getName());

		System.out.print("Press enter to continue");
		System.in.read();

		a.setName("After A");

		System.out.println("a.getName() = " + a.getName());

		System.out.print("Press enter to continue");
		System.in.read();

		System.out.println("rollback()");

		t.rollback();

		System.out.println("finished " + t.getState());

		System.out.println("a.getName() = " + a.getName());

		Thread.sleep(1000000);
	}
}
