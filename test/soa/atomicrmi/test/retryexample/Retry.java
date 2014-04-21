package soa.atomicrmi.test.retryexample;

import java.rmi.RemoteException;
import java.rmi.registry.Registry;

import soa.atomicrmi.Transactable;
import soa.atomicrmi.Transaction;

public class Retry {
	public void test(Registry rmiRegistry, A remoteA) throws RemoteException {
		Transaction t = new Transaction(rmiRegistry);
		final A a = (A) t.accesses(remoteA, 2);

		t.start(new Transactable() {
			public void atomic(Transaction t) throws RemoteException {
				if (a.getValue() < 100)
					t.retry();
				a.setValue(0);
				t.commit();
			}
		});
	}
}
