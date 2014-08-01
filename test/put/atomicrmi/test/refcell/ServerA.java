package put.atomicrmi.test.refcell;

import java.rmi.AccessException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import put.atomicrmi.TransactionalUnicastRemoteObject;

public class ServerA extends TransactionalUnicastRemoteObject implements ReferrenceCell {

	protected ServerA() throws RemoteException {
		super();
	}

	private static final long serialVersionUID = 393481853233863951L;

	private int contents = 0;

	private static void run(String host, int port) throws RemoteException, AccessException {
		Registry registry = LocateRegistry.getRegistry(host, port);
		// XXX rem TransactionsLock.initialize(registry);

		ReferrenceCell cell = new ServerA();
		registry.rebind("ServerA", cell);

		System.out.println("ServerA bound in registry");
	}

	public void set(int v) {
		contents = v;
	}

	public int get() {
		return contents;
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
				System.out.println("Usage: java " + ServerA.class.getSimpleName() + " [[host] port]");

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
}
