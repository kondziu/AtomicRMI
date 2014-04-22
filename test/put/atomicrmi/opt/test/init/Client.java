package put.atomicrmi.opt.test.init;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import put.atomicrmi.opt.Transaction;
import put.sync.Barrier;
import put.sync.DistributedBarrier;

public class Client extends Thread {
	
	static final int N = 20;
    static final Barrier barrier;
    
    static {
    	Barrier temp = null;
    	try {
		    temp = new DistributedBarrier(N);
		} catch (RemoteException | IllegalArgumentException e) {
			e.printStackTrace();
			System.exit(1);
		}
    	barrier = temp;
    }

	public static void main(String[] args) throws InterruptedException {
		// Get a reference to RMI registry.

		Client[] clients = new Client[N];
		for (int i = 0; i < N; i++) {
			clients[i] = new Client();
		}
		
		for (int i = 0; i < N; i++) {
			clients[i].start();
		}
		
		for (int i = 0; i < N; i++) {
			clients[i].join();
		}
	}

	@Override
	public void run() {
		try {
			List<Account> accounts = new ArrayList<>(6);

			Registry registry1 = LocateRegistry.getRegistry("localhost", Server1.RMI_PORT);
			Registry registry2 = LocateRegistry.getRegistry("localhost", Server2.RMI_PORT);
			
			// Get references to remote objects.
			accounts.add((Account) registry1.lookup("A"));
			accounts.add((Account) registry1.lookup("B"));
			accounts.add((Account) registry1.lookup("C"));
						
			// Get references to remote objects.
			accounts.add((Account) registry2.lookup("C"));
			accounts.add((Account) registry2.lookup("B"));
			accounts.add((Account) registry2.lookup("C"));

			Collections.shuffle(accounts);

			// Wait for the user.
			System.out.println("About to run transactional code.");

			// Transaction header.
			Transaction transaction = new Transaction();
			Account ta = transaction.accesses(accounts.get(0), 3);
			Account tb = transaction.accesses(accounts.get(1), 3);

			barrier.enter();
			transaction.start();

			// Check balance on both accounts.
			System.out.println("Balance on 0: " + ta.getBalance());
			System.out.println("Balance on 1: " + tb.getBalance());

			// Transfer funds from A to B.
			System.out.println("Transfering 100 from 0 to 1.");
			ta.withdraw(100);
			tb.deposit(100);

			// Check balance again.
			System.out.println("New balance on 0: " + ta.getBalance());
			System.out.println("New balance on 1: " + tb.getBalance());

			transaction.commit();

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}
}
