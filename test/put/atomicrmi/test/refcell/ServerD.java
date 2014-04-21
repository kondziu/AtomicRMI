package soa.atomicrmi.test.refcell;

import java.rmi.AccessException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import put.atomicrmi.opt.TransactionalUnicastRemoteObject;

public class ServerD extends TransactionalUnicastRemoteObject implements ReferrenceCellPubSubServer {

	protected ServerD() throws RemoteException {
		super();
	}

	private static final long serialVersionUID = -7219826938495430613L;

	private static void run(String host, int port) throws RemoteException, AccessException {
		Registry registry = LocateRegistry.getRegistry(host, port);
		// XXX rem TransactionsLock.initialize(registry);

		ReferrenceCellPubSubServer cell = new ServerD();
		registry.rebind("ServerD", cell);

		System.out.println("ServerD bound in registry");
	}

	public static void main(String args[]) {

		// default values for host & port
		String host = "localhost";
		int port = 1099;

		if (args.length > 0)
			if (args.length == 2) {
				host = args[0];
				port = Integer.valueOf(args[1]);
			} else if (args.length == 1)
				port = Integer.valueOf(args[0]);
			else {
				System.out.println("Usage: java " + ServerC.class.getSimpleName() + " [[host] port]");

				System.exit(1);
			}

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new RMISecurityManager());
		}
		try {
			run(host, port);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Map<Integer, Set<ReferrenceCellClient>> clients = new HashMap<Integer, Set<ReferrenceCellClient>>();
	private Map<Integer, Cell> cells = new HashMap<Integer, Cell>();

	public void publish(Integer key, Cell cell) throws RemoteException {
		cells.put(key, cell);
		deliverToAll(key);
	}

	public void subscribe(ReferrenceCellClient client, Integer key) throws RemoteException {
		Set<ReferrenceCellClient> set;
		set = clients.get(key);
		if (set == null) {
			set = new HashSet<ReferrenceCellClient>();
			set.add(client);
			clients.put(key, set);
		} else {
			set.add(client);
			clients.put(key, set);
		}
		deliverToAll(key);
	}

	private void deliverToAll(Integer key) {
		Set<ReferrenceCellClient> set = clients.get(key);
		if (set == null || set.isEmpty()) {
			System.out.println("No client subscribed for the key");
			return;
		}

		Iterator<ReferrenceCellClient> iterator = set.iterator();
		while (iterator.hasNext()) {
			ReferrenceCellClient client = iterator.next();
			deliverToClient(client, key);
		}
	}

	private void deliverToClient(ReferrenceCellClient client, Integer key) {
		Cell o = cells.get(key);

		if (o == null) {
			System.out.println("No object with such key published");
			return;
		}

		try {

			client.callback(o); // deliver subscribed object to the client
			// iterator.remove(); // remove the client from map of subscriptions

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
