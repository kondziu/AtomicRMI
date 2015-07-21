package put.util.ids;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface IdentifiableRemote extends Remote {
	/**
	 * Returns the unique identifier of this remote object. 
	 * 
	 * <p>
	 * The implementation should ensure that all objects are uniquely identifiable.
	 * 
	 * @return unique identifier
	 * @throws RemoteException
	 */
	UUID getUID() throws RemoteException;
}
