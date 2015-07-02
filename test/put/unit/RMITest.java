package put.unit;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import org.junit.After;
import org.junit.Before;

import put.unit.vars.Variable;
import put.unit.vars.VariableImpl;

public class RMITest {
	protected Registry registry;

	/**
	 * Helper function to get the state of a variable (non-transacitonally).
	 * 
	 * @param var
	 * @return value of var
	 */
	public int state(String var) throws RemoteException, NotBoundException {
		return ((Variable) registry.lookup(var)).read();
	}

	/**
	 * Create registry (port 1995) and transactional remote objects.
	 * 
	 * Creates objects x, y, z.
	 * 
	 * @throws Exception
	 */
	@Before
	public void populate() throws Exception {
		Thread.sleep(100);
		registry = LocateRegistry.createRegistry(1115);
		registry.bind("x", new VariableImpl("x", 0));
		registry.bind("y", new VariableImpl("y", 0));
		registry.bind("z", new VariableImpl("z", 0));
	}

	/**
	 * Remove transactional remote objects and registry.
	 * 
	 * @throws Exception
	 */
	@After
	public void depopulate() throws Exception {
		registry.unbind("x");
		registry.unbind("y");
		registry.unbind("z");
		UnicastRemoteObject.unexportObject(registry, true);
	}
}
