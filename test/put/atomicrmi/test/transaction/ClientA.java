package soa.atomicrmi.test.transaction;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.opt.Transaction;

public class ClientA {
	public static void main(String[] args) throws AlreadyBoundException, NotBoundException, IOException {
		Registry registry = LocateRegistry.getRegistry(1099);

		// registry.bind("B", new ImplA());
		// /A a = (A) registry.lookup("A");

		Transaction t = new Transaction();
		A a = (A) t.accesses(registry.lookup("A"));

		t.start();

		System.out.println(a.getName());
		System.in.read();

		a.setName("After A");
		System.out.println(a.getName());

		System.out.println("commit()");

		t.commit();

		System.out.println("done.");
	}
}
