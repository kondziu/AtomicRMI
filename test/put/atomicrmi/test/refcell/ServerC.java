package put.atomicrmi.test.refcell;

import java.rmi.AccessException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Calendar;

import put.atomicrmi.opt.TransactionalUnicastRemoteObject;

public class ServerC extends TransactionalUnicastRemoteObject implements ReferrenceCellRequestServer {

	protected ServerC() throws RemoteException {
		super();
	}

	private static final long serialVersionUID = -7219826938495430613L;

	private static void run(String host, int port) throws RemoteException, AccessException {
		Registry registry = LocateRegistry.getRegistry(host, port);
		// XXX rem TransactionsLock.initialize(registry);

		ReferrenceCellRequestServer cell = new ServerC();
		registry.rebind("ServerC", cell);

		System.out.println("ServerC bound in registry");
	}

	public void requestCell(ReferrenceCellClient client) throws RemoteException {
		Cell cell = new CellImpl();

		Calendar rightNow = Calendar.getInstance();
		cell.set(rightNow.get(Calendar.SECOND));

		try {
			client.callback(cell);
		} catch (Exception e) {
			e.printStackTrace();
		}
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
}
