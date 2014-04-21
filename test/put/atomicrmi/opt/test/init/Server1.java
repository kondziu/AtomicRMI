package soa.atomicrmi.opt.test.init;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server1 {
	public static final int RMI_PORT = 9001;

	public static void main(String[] args) throws RemoteException {
		// Get a reference to RMI registry.
		Registry registry = LocateRegistry.createRegistry(RMI_PORT);

		// Bind addresses.
		registry.rebind("A", new AccountImpl("A", 1000));
		registry.rebind("B", new AccountImpl("B", 1000));
		registry.rebind("C", new AccountImpl("C", 1000));
	}
}
