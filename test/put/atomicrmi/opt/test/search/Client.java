package put.atomicrmi.opt.test.search;

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
	
	static final int N = 5;
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

	private int id;

	public Client(int i) {
		this.id = i;
	}

	public static void main(String[] args) throws InterruptedException {
		// Get a reference to RMI registry.

		Client[] clients = new Client[N];
		for (int i = 0; i < N; i++) {
			clients[i] = new Client(i);
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
			List<Account> accounts = new ArrayList<>(3);

			Registry registry = LocateRegistry.getRegistry("localhost", Server.RMI_PORT);
			
			// Get references to remote objects.
			accounts.add((Account) registry.lookup("A"));
			accounts.add((Account) registry.lookup("B"));
			accounts.add((Account) registry.lookup("C"));
			//Collections.shuffle(accounts);

			// Wait for the user.
			System.out.println(id + " about to run transactional code.");

			// Transaction header.
			Transaction transaction = new Transaction();
			accounts = transaction.accesses(accounts);

			barrier.enter();
			System.out.println(id + " past the barrier");
			transaction.start();
			
			for(Account account : accounts) {
				if (account.getName().equals("C")) {
					System.out.println(id + " " + account.getName() + ": " + account.getBalance());
					Thread.sleep(5000);
					account.deposit(100);
				} else {
					System.out.println(id + " releasing " + account.getName());
					transaction.release(account);
					System.err.println(account.getName());
				}
			}

			System.out.println(id + " committing");
			transaction.commit();
			System.out.println(id + " committed");
			
			System.err.println(accounts.get(0).getName());

			
			barrier.enter();

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}
}
