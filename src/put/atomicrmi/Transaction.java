/*
 * Atomic RMI
 *
 * Copyright 2009-2010 Wojciech Mruczkiewicz <Wojciech.Mruczkiewicz@cs.put.poznan.pl>
 *                     Pawel T. Wojciechowski <Pawel.T.Wojciechowski@cs.put.poznan.pl>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details
 *
 * You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package put.atomicrmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import put.atomicrmi.Access.Mode;

/**
 * The main class for controlling transaction life time. Provides methods to
 * start and terminate the transaction. Contains the heartbeater implementation
 * required for failure detection mechanism.
 * 
 * This class operates under the assumption that more than one registry is
 * present and therefore more than one "global" lock needs to be acquired to
 * properly initialize this transaction.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 * 
 */
public class Transaction extends UnicastRemoteObject implements ITransaction {

	
	

	/**
	 */
	private static final long serialVersionUID = -1134870682188058024L;

	/**
	 * Default, infinite value of an upper bound on number of remote object
	 * invocations.
	 */
	public static final int INF = -1;

	/**
	 * Transaction state that is set before transaction start.
	 */
	public static final int STATE_PREPARING = 1;

	/**
	 * Transaction state that defines running transaction.
	 */
	public static final int STATE_RUNNING = 2;

	/**
	 * Transaction state that is set after commit.
	 */
	public static final int STATE_COMMITED = 3;

	/**
	 * Transaction state that is set after roll-back.
	 */
	public static final int STATE_ROLLEDBACK = 4;

	/**
	 * Current transaction state.
	 */
	protected int state;

	/**
	 * Randomly generated transaction unique identifier.
	 */
	protected UUID id;

	// /**
	// * Reference to remote global lock used only for transaction startup.
	// */
	// private ITransactionsLock[] globalLocks;

	/**
	 * List of proxies of the accessed remote objects.
	 */
	protected List<IObjectProxy> proxies;

	/**
	 * This transaction's read set.
	 * 
	 * <p>
	 * It is assumed that read set and write set are disjoint.
	 */
//	private Set<IObjectProxy> readonly;

	/**
	 * This transaction's write set.
	 * 
	 * <p>
	 * It is assumed that read set and write set are disjoint.
	 */
//	private Set<IObjectProxy> writeonly;

	/**
	 * Heartbeater's thread reference.
	 */
	protected Thread heartbeatThread;

	/**
	 * Creates new transaction. The required argument is a JavaRMI registry
	 * instance. It is used to obtain global lock instance.
	 * 
	 * @param registries
	 *            a references to remote registries.
	 * @throws RemoteException
	 *             when remote exception occurs during retrieval of remote
	 *             global lock.
	 * 
	 */
	public Transaction() throws RemoteException {
		state = STATE_PREPARING;
		id = UUID.randomUUID();

		// for (int i = 0; i < registries.length; i++) {
		// globalLocks[i] = TransactionsLock.getOrCreate(registries[i]);
		// }
		//
		// // Sorting prevents deadlocks.
		// Arrays.sort(globalLocks);

		OneHeartbeat.thread.register(id);
		proxies = new ArrayList<IObjectProxy>();

		// XXX probably redundant
//		readonly = new HashSet<IObjectProxy>();
//		writeonly = new HashSet<IObjectProxy>();
	}

	/**
	 * Gives the transaction randomly generated unique identifier.
	 * 
	 * @return transaction unique identifier.
	 */
	public UUID getId() {
		return id;
	}

	/**
	 * Gives the current transaction state.
	 * 
	 * @return current transaction state.
	 */
	public int getState() {
		return state;
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with
	 * infinite upper bound on number of this object invocations.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public <T> T accesses(T obj) throws TransactionException {
		return accesses(obj, INF, INF, INF, Mode.ANY);
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with
	 * given upper bound on number of this object invocations. The object is
	 * wrapped by special object proxy and returned. During transaction
	 * execution only this proxy must be used to guarantee atomicity and
	 * isolation properties.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @param calls
	 *            an upper bound on number of invocation to this remote object.
	 * @param mode
	 *            object to be opened either in read-only, write-only, or
	 *            read-write mode
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public <T> T accesses(T obj, long calls, Mode mode) throws TransactionException {
		return accesses(obj, calls, calls, calls, mode);
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with the
	 * given upper bound on number of this object invocations.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @param calls
	 *            the maximum number of time this object will be accessed within
	 *            the transaction
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public <T> T accesses(T obj, int calls) throws TransactionException {
		return accesses(obj, calls, calls, calls, Mode.ANY);
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with
	 * infinite upper bound on number of this object invocations. The object
	 * will only be read from.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public <T> T reads(T obj) throws TransactionException {
		return accesses(obj, INF, 0L, INF, Mode.READ_ONLY);
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with the
	 * given upper bound on number of this object invocations. The object will
	 * only be read from.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @param calls
	 *            the maximum number of time this object will be accessed within
	 *            the transaction
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public <T> T reads(T obj, int calls) throws TransactionException {
		return accesses(obj, calls, calls, 0L, Mode.READ_ONLY);
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with
	 * infinite upper bound on number of this object invocations. The object
	 * will only be written to.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public <T> T writes(T obj) throws TransactionException {
		return accesses(obj, INF, 0L, INF, Mode.WRITE_ONLY);
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with the
	 * given upper bound on number of this object invocations. The object will
	 * only be written to.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @param calls
	 *            the maximum number of time this object will be accessed within
	 *            the transaction
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public <T> T writes(T obj, int calls) throws TransactionException {
		return accesses(obj, calls, 0L, calls, Mode.WRITE_ONLY);
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with
	 * given upper bound on number of this object invocations. The object is
	 * wrapped by special object proxy and returned. During transaction
	 * execution only this proxy must be used to guarantee atomicity and
	 * isolation properties.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @param calls
	 *            an upper bound on number of invocation to this remote object.
	 * @param writes
	 *            an upper bound on the number of writes to this remote object.
	 * @param reads
	 *            an upper bound on the number of reads to this remote object.
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public <T> T accesses(T obj, int allCalls, int reads, int writes) throws TransactionException {
		return accesses(obj, allCalls, reads, writes, (reads == 0 ? Mode.WRITE_ONLY : (writes == 0 ? Mode.READ_ONLY
				: Mode.ANY)));
	}

	/**
	 * Adds given remote object to the list of accessed remote objects with
	 * given upper bound on number of this object invocations. The object is
	 * wrapped by special object proxy and returned. During transaction
	 * execution only this proxy must be used to guarantee atomicity and
	 * isolation properties.
	 * 
	 * @param obj
	 *            remote object accessed by transaction.
	 * @param calls
	 *            an upper bound on number of invocation to this remote object.
	 * @param writes
	 *            an upper bound on the number of writes to this remote object.
	 * @param mode
	 *            object to be opened either in read-only, write-only, or
	 *            read-write mode
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	@SuppressWarnings("unchecked")
	public <T> T accesses(T obj, long allCalls, long reads, long writes, Mode mode) throws TransactionException {
		if (allCalls != INF && allCalls < 1)
			throw new TransactionException("Invalid upper bound: zero or negative number of invocations (" + allCalls
					+ ").");

		if (writes != INF && writes < 0)
			throw new TransactionException("Invalid upper bound: negative number of writes (" + writes + ").");

		if (allCalls != INF && writes == INF)
			throw new TransactionException("Invalid upper bound: more writes (INF) than all calls (" + allCalls + ").");

		if (allCalls != INF && writes > allCalls)
			throw new TransactionException("Invalid upper bound: more writes (" + writes + ") than all calls ("
					+ (allCalls == INF ? "INF" : allCalls) + ").");

		if (state != STATE_PREPARING)
			throw new TransactionException("Object access information can be added only in preparation state.");

		try {
			ITransactionalRemoteObject remote = (ITransactionalRemoteObject) obj;
			IObjectProxy proxy = (IObjectProxy) remote.createProxy(this, id, allCalls, reads, writes, mode);
			proxies.add(proxy);

			OneHeartbeat.thread.addFailureMonitor(id, remote.getFailureMonitor());
			return (T) proxy;
		} catch (RemoteException e) {
			throw new TransactionException("Unable to create proxy for an object.", e);
		}
	}

	/**
	 * Starts and executes the transaction given by {@link Transactional}
	 * implementation. This method performs transaction initialization (obtains
	 * global locks and starts the heartbeater). It allows to call
	 * {@link Transaction#retry()} which will cause re-execution of given
	 * transaction. Transaction is also re-executed when forced rollback occurs
	 * or remote exception is detected.
	 * 
	 * If no commit or roll-back operation is performed by the transaction then
	 * commit action is assumed by default.
	 * 
	 * @param transaction
	 *            implementation of a transaction method.
	 * @throws TransactionException
	 *             when execution of a transaction was unsuccessful after
	 *             multiple retries.
	 */
	public void start(Transactional transaction) throws TransactionException {
		int restartsByFailure = 0;

		while (true) {
			try {
				try {
					start();

					transaction.atomic(this);

					if (getState() == STATE_RUNNING)
						commit();

					break;
				} catch (RetryCalledException e) {
					rollback();
				} catch (RollbackForcedException e) {
					// Transaction already rolled-back, no rollback required.
				}
			} catch (RemoteException e) {
				// Retry caused by system failure. This situation should be
				// monitored and handled separately.
				System.err.println(e.getMessage());
				e.printStackTrace();
				if (++restartsByFailure == 5)
					throw new TransactionException("Fatal error after multiple restarts of transaction.");
				rollback();
			}

			// Wait for heartbeater
			while (true) {
				try {
					heartbeatThread.join();
					break;
				} catch (InterruptedException e) {
				}
			}

			state = STATE_PREPARING;
		}
	}

	/**
	 * Starts the transaction. This method is responsible for obtaining global
	 * locks of the accessed object proxies and starts the heartbeater required
	 * for failure monitor.
	 * 
	 * @throws TransactionException
	 *             when remote error during transaction initialization occurs.
	 */
	public void start() throws TransactionException {
		try {
//			heartbeatThread = new Thread(heartbeat, "Heartbeat for " + id);
//			heartbeatThread.start();


			// Arrays.sort(proxies);

			Collections.sort(proxies, comparator);

			for (IObjectProxy proxy : proxies) {
				proxy.lock();
			}

			for (IObjectProxy proxy : proxies) {
				proxy.startTransaction();
			}

			for (IObjectProxy proxy : proxies) {
				proxy.unlock();
			}

		} catch (RemoteException e) {
			throw new TransactionException("Unable to initialize transaction.", e);
		}

		setState(STATE_RUNNING);
	}

	/**
	 * Commit changes made by this transaction and terminates transaction.
	 * 
	 * @throws TransactionException
	 *             when method was called before transaction start or after
	 *             transaction end.
	 * @throws RollbackForcedException
	 *             when commit action was not successful and changes were forced
	 *             to be rolled-back.
	 */
	public void commit() throws TransactionException, RollbackForcedException {
		if (!waitForSnapshots()) {
//			System.out.println("FP C1");
			finishProxies(true);
			setState(STATE_ROLLEDBACK);
			
//			synchronized (heartbeat) {
				OneHeartbeat.thread.remove(id);
//			}
			
			throw new RollbackForcedException("Rollback forced during commit.");
		}

//		System.out.println("FP C2");
		finishProxies(false);
		setState(STATE_COMMITED);

		// Signal heartbeater to stop.
//		synchronized (heartbeat) {
		OneHeartbeat.thread.remove(id);
//		}

		// heartbeatThread.stop();
	}

	/**
	 * Rolls-back changes made by this transaction. Every remote object that was
	 * modified by this transaction is restored to state before atomic
	 * execution.
	 * 
	 * @throws TransactionException
	 *             when rollback method was called before transaction start or
	 *             after transaction end.
	 */
	public void rollback() throws TransactionException {
		waitForSnapshots();
//		System.out.println("FP R");
		finishProxies(true);
		setState(STATE_ROLLEDBACK);

		// Signal heartbeater to stop.
		OneHeartbeat.thread.remove(id);

		// heartbeatThread.stop();
	}

	/**
	 * Rolls-back all the changes made by this transaction and re-executes it.
	 * This method can be called only when transaction was started using the
	 * {@link Transaction#start(Transactional)} method.
	 */
	public void retry() throws RemoteException {
		throw new RetryCalledException();
	}

	/**
	 * Waits for all remote objects to be ready for commit or roll-back
	 * operations. Determines if commit is allowed or roll-back must be
	 * performed.
	 * 
	 * @return true when commit is allowed, false when roll-back operation is
	 *         forced and must be performed.
	 */
	protected boolean waitForSnapshots() {
		boolean commit = true;

		// TODO parallel for
		for (IObjectProxy proxy : proxies) {
			try {
				// TODO commit = commit && proxy.waitForSnapshots(); ?
				if (!proxy.waitForSnapshot(false))
					commit = false;
			} catch (RemoteException e) {
				commit = false;
			}
		}

		return commit;
	}

	/**
	 * Release object early.
	 * 
	 * @param object
	 * @throws TransactionException
	 * @throws RemoteException
	 */
	public <T> void release(T object) throws TransactionException, RemoteException {
		if (object instanceof IObjectProxy) {
			IObjectProxy proxy = (IObjectProxy) object;
			proxy.free();
		} else {
			throw new TransactionException("Not a transactional object: " + object);
		}
	}

	/**
	 * Finalizes and releases all remote objects accessed by this transaction.
	 * 
	 * @param restore
	 *            determines if changes made by this transaction should be
	 *            restored or committed.
	 */
	protected void finishProxies(boolean restore) {
		// TODO parallel for
		for (IObjectProxy proxy : proxies) {
			try {
//				System.out.println("T finishing proxies");
				proxy.finishTransaction(restore, false);
			} catch (RemoteException e) {
				// Do nothing. This situation is treated as remote object
				// failure would occur after this operation.
			}
		}
	}

	/**
	 * Performs state transition and checks this transition validity.
	 * 
	 * @param newState
	 *            new transaction state.
	 * @throws TransactionException
	 *             when transition to this state is invalid at given current
	 *             transaction state.
	 */
	protected void setState(int newState) throws TransactionException {
		switch (state) {
		case STATE_PREPARING:
			if (newState != STATE_RUNNING)
				throw new TransactionException("Invalid transaction state transition.");
			break;
		case STATE_RUNNING:
			if (newState != STATE_COMMITED && newState != STATE_ROLLEDBACK)
				throw new TransactionException("Invalid transaction state transition.");
			break;
		case STATE_COMMITED:
		case STATE_ROLLEDBACK:
			throw new TransactionException("Invalid transaction state transition.");
		}

		state = newState;
	}

	/**
	 * A comparator object for sorting remote object proxies by their IDs.
	 */
	protected Comparator<IObjectProxy> comparator = new Comparator<IObjectProxy>() {
//		@SuppressWarnings({ "unchecked", "rawtypes" })
		public int compare(IObjectProxy a, IObjectProxy b) {
			try {
				return a.getUID().compareTo(b.getUID());
			} catch (RemoteException e) {
				// Nasty hack
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}

	};

	@Override
	public UUID getUID() throws RemoteException {
		return id;
	}
}
