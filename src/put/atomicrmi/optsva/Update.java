package put.atomicrmi.optsva;

import java.rmi.RemoteException;
import java.util.Collections;
import java.util.concurrent.Callable;

import put.atomicrmi.optsva.Access.Mode;
import put.atomicrmi.optsva.objects.ObjectProxy;
import put.atomicrmi.optsva.sync.Heartbeat;

public class Update extends Transaction {

	private static final long serialVersionUID = 1157466041881814566L;

	public Update() throws RemoteException {
		super();
		
		Heartbeat.thread.register(id);
	}

	@Override
	public <T> T accesses(T obj) throws TransactionException {
		return accesses(obj, Transaction.INF);
	}

	@Override
	public <T> T accesses(T obj, int allCalls, int reads, int writes) throws TransactionException {
		if (reads == 0 && allCalls == writes) {
			return accesses(obj, writes);
		}
		throw new TransactionException("Invalid access preamble for write-only transaction");
	}

	@Override
	public <T> T accesses(T obj, int calls) throws TransactionException {
		return accesses(obj, calls, Mode.WRITE_ONLY);
	}

	@Override
	public <T> T accesses(T obj, long allCalls, long reads, long writes, Mode mode) throws TransactionException {
		if (mode == Mode.WRITE_ONLY && reads == 0 && allCalls == writes) {
			return accesses(obj, writes);
		}
		throw new TransactionException("Invalid access preamble for write-only transaction");
	}

	@Override
	public <T> T accesses(T obj, long calls, Mode mode) throws TransactionException {
		if (mode == Mode.WRITE_ONLY) {
			return accesses(obj, calls);
		}
		throw new TransactionException("Invalid access mode for write-only transaction: " + mode);
	}

	@SuppressWarnings("unchecked")
	public <T> T accesses(T obj, long writes) throws TransactionException {
		if (writes != INF && writes < 1)
			throw new TransactionException("Invalid upper bound: negative number of writes (" + writes + ").");

		if (state != State.PREPARING)
			throw new TransactionException("Object access information can be added only in preparation state.");

		try {
			TransactionalRemoteObject remote = (TransactionalRemoteObject) obj;
			ObjectProxy proxy = (ObjectProxy) remote.createUpdateProxy(this, id, writes);
			proxies.add(proxy);

			// XXX possibly remove until actually starting writes?
			Heartbeat.thread.addFailureMonitor(id, remote.getFailureMonitor());
			return (T) proxy;
		} catch (RemoteException e) {
			throw new TransactionException("Unable to create proxy for an object.", e);
		}
	}

	@Override
	public void start() throws TransactionException {
		// Intentionally left empty.
	}

	@Override
	protected boolean waitForSnapshots() {
		boolean commit = true;

		for (ObjectProxy proxy : proxies) {
			try {
				proxy.update();

				if (!proxy.waitForSnapshot(false))
					commit = false;
			} catch (RemoteException e) {
				commit = false;
			}
		}

		return commit;
	}

	public void commit() throws TransactionException, RollbackForcedException {

		/** Start */
		try {
			Heartbeat.thread.register(id);

			Collections.sort(proxies, comparator);

			for (ObjectProxy proxy : proxies) {
				proxy.lock();
			}

			for (ObjectProxy proxy : proxies) {
				proxy.startTransaction();
			}

			for (ObjectProxy proxy : proxies) {
				proxy.unlock();
			}

		} catch (RemoteException e) {
			throw new TransactionException("Unable to initialize transaction.", e);
		}

		setState(State.RUNNING);

		/** Update and wait for snapshots*/
		boolean commit = waitForSnapshots();

		/** Commit begin */
		if (!commit) {
			finishProxies(true);
			setState(State.ABORTED);
			throw new RollbackForcedException("Rollback forced during commit.");
		}

		finishProxies(false);
		setState(State.COMMITED);

		/** Signal heartbeater to stop. */
		Heartbeat.thread.remove(id);
	}

	/**
	 * I need this for testing cascading aborts, but I don't want to put it into
	 * the original commit, so this is the same code as commit (or it should
	 * be).
	 * 
	 * <p>
	 * Sorry, future developer.
	 * 
	 * @param postStart
	 * @param postSnapshots
	 * @throws TransactionException
	 * @throws RollbackForcedException
	 */
	public <T> void commitInterrupted(Callable<T> postStart, Callable<T> postSnapshots) throws TransactionException,
			RollbackForcedException {

		try {			
			Collections.sort(proxies, comparator);

			for (ObjectProxy proxy : proxies) {
				proxy.lock();
			}

			for (ObjectProxy proxy : proxies) {
				proxy.startTransaction();
			}

			for (ObjectProxy proxy : proxies) {
				proxy.unlock();
			}

		} catch (RemoteException e) {
			throw new TransactionException("Unable to initialize transaction.", e);
		}

		setState(State.RUNNING);

		if (postStart != null) {
			try {
				postStart.call();
			} catch (Exception e) {
				e.printStackTrace();
				throw new TransactionException(e.getLocalizedMessage(), e.getCause());
			}
		}

		boolean commit = waitForSnapshots();

		if (postSnapshots != null) {
			try {
				postSnapshots.call();
			} catch (Exception e) {
				e.printStackTrace();
				throw new TransactionException(e.getLocalizedMessage(), e.getCause());
			}
		}

		if (!commit) {
			finishProxies(true);
			setState(State.ABORTED);
			throw new RollbackForcedException("Rollback forced during commit.");
		}

		finishProxies(false);
		setState(State.COMMITED);

		Heartbeat.thread.remove(id);
	}

	@Override
	public void rollback() throws TransactionException {
		// nothing
	}

	@Override
	public <T> void release(T object) throws TransactionException, RemoteException {
		// nothing
	}
}
