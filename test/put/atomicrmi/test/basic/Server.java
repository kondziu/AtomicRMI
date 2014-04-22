package put.atomicrmi.test.basic;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {

	public static void main(String[] args) throws RemoteException, AlreadyBoundException {
		A a = new ImplA();
		a.setName("Object A");

		Registry registry = LocateRegistry.createRegistry(1099);
		registry.bind("A", a);
	}
}
