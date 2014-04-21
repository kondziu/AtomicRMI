package soa.atomicrmi.test.transaction;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.Transaction;

public class ClientB {
	public static void main(String[] args) throws AlreadyBoundException, NotBoundException, IOException {
		Registry registry = LocateRegistry.getRegistry(1099);

		Transaction t = new Transaction(registry);
		A a = (A) t.accesses(registry.lookup("A"));

		t.start();

		System.out.println(a.getName());
		System.in.read();

		a.setName("After B");
		System.out.println(a.getName());

		t.commit();
	}
}
