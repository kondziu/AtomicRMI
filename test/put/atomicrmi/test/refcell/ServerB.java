package put.atomicrmi.test.refcell;

import java.rmi.AccessException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import put.atomicrmi.opt.TransactionalUnicastRemoteObject;

public class ServerB extends TransactionalUnicastRemoteObject implements ReferrenceCellServer {

	protected ServerB() throws RemoteException {
		super();
	}

	private static final long serialVersionUID = -7219826938495430613L;

	private static void run(String host, int port) throws RemoteException, AccessException {
		Registry registry = LocateRegistry.getRegistry(host, port);
		// XXX rem TransactionsLock.initialize(registry);

		ReferrenceCellServer cell = new ServerB();
		registry.rebind("ServerB", cell);

		System.out.println("ServerB bound in registry");
	}

	Map<Integer, Cell> cells = new HashMap<Integer, Cell>();

	public Cell getCell(Integer index) throws RemoteException {
		Calendar rightNow = Calendar.getInstance();

		Cell cell = new CellImpl();
		cell.set(rightNow.get(Calendar.SECOND));

		return cell;
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
				System.out.println("Usage: java " + ServerB.class.getSimpleName() + " [[host] port]");

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
