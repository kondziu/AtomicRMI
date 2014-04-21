package crash;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import soa.atomicrmi.TransactionException;

public class Server {
	public void bind() throws RemoteException, AlreadyBoundException, TransactionException {
		A a = new ImplA();
		a.setName("Before");

		Registry registry = LocateRegistry.createRegistry(1099);
		registry.bind("A", a);

		System.out.println(a.getName());
	}

	public static void main(String[] args) throws RemoteException, AlreadyBoundException, NotBoundException,
			TransactionException {
		Server server = new Server();
		server.bind();
	}
}
