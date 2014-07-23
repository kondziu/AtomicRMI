package soa.atomicrmi.test.search;



import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {
	public static final int RMI_PORT = 9001;

	public static void main(String[] args) throws RemoteException {
		// Get a reference to RMI registry.
		Registry registry = LocateRegistry.createRegistry(RMI_PORT);

		// Bind addresses.
		registry.rebind("A", new AccountImpl("A", 700));
		registry.rebind("B", new AccountImpl("B", 800));
		registry.rebind("C", new AccountImpl("C", 900));
	}
}