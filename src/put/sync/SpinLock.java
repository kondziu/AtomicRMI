package put.sync;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class SpinLock extends UnicastRemoteObject  implements Lock {

	public SpinLock() throws RemoteException {
		super();
	}

	private static final long serialVersionUID = -5764934103189787441L;

	private static final String NULL = "nobody";
		
	private CasObject<Object> owner = new CasObject<Object>(NULL);

	public void lock(Object id) throws RemoteException {
		while (!owner.compareAndSwap(NULL, id));
	}

	public void release(Object id) throws RemoteException {
		if (!owner.compareAndSwap(id, NULL)) {
			throw new RemoteException(
					"Consistency error: spinlock owned by 2 or more owners.");
		}
	}

}
