package put.atomicrmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Stateful extends Remote {

	/**
	 * Helper class: primitives and universal object types.
	 * 
	 * @author Konrad Siek
	 */
	static enum FieldType {
		Int, Float, Byte, Char, Object, Double, Long, Short, Boolean
	}

	void set(String field, Stateful.FieldType left, Object right) throws RemoteException;

}
