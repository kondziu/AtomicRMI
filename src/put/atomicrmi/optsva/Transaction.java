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
package put.atomicrmi.optsva;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import put.atomicrmi.optsva.Access.Mode;
import put.atomicrmi.optsva.objects.ObjectProxy;
import put.atomicrmi.optsva.sync.Heartbeat;

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
public class Transaction extends UnicastRemoteObject implements TransactionRef {

	/**
	 * Generated serial version number.
	 */
	private static final long serialVersionUID = -1134870682188058024L;

	/**
	 * Default, infinite value of an upper bound on number of remote object
	 * invocations.
	 */
	public static final int INF = -1;

	enum State {
		/**
		 * Transaction state that is set before transaction start.
		 */
		PREPARING,
		/**
		 * Transaction state that defines running transaction.
		 */
		RUNNING,
		/**
		 * Transaction state that is set after commit.
		 */
		COMMITED,
		/**
		 * Transaction state that is set after roll-back.
		 */
		ABORTED
	}

	/**
	 * Current transaction state.
	 */
	protected State state;

	/**
	 * Randomly generated transaction unique identifier.
	 */
	protected final UUID id;

	/**
	 * List of proxies of the accessed remote objects.
	 */
	protected final List<ObjectProxy> proxies;

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
		state = State.PREPARING;
		id = UUID.randomUUID();

		Heartbeat.thread.register(id);
		proxies = new ArrayList<ObjectProxy>();
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
	public State getState() {
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

		if (state != State.PREPARING)
			throw new TransactionException("Object access information can be added only in preparation state.");

		try {
			TransactionalRemoteObject remote = (TransactionalRemoteObject) obj;
			ObjectProxy proxy = (ObjectProxy) remote.createProxy(this, id, allCalls, reads, writes, mode);
			proxies.add(proxy);

			Heartbeat.thread.addFailureMonitor(id, remote.getFailureMonitor());
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

					if (getState() == State.RUNNING)
						commit();

					break;

				} catch (RetryCalledException e) {
					rollback();
				} catch (RollbackForcedException e) {
					// Transaction already aborted no abort required.
				}

			} catch (RemoteException e) {
				/**
				 * Retry caused by system failure. This situation should be
				 * monitored and handled separately.
				 */
				System.err.println(e.getMessage());
				e.printStackTrace();
				if (++restartsByFailure == 5)
					throw new TransactionException("Fatal error after multiple restarts of transaction.");
				rollback();
			}

			state = State.PREPARING;
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

			finishProxies(true);
			setState(State.ABORTED);

			Heartbeat.thread.remove(id);

			throw new RollbackForcedException("Rollback forced during commit.");
		}

		finishProxies(false);
		setState(State.COMMITED);

		/** Signal heartbeater to stop. */
		Heartbeat.thread.remove(id);
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

		finishProxies(true);
		setState(State.ABORTED);

		/** Signal heartbeater to stop. */
		Heartbeat.thread.remove(id);
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

		for (ObjectProxy proxy : proxies) {
			try {
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
		if (object instanceof ObjectProxy) {
			ObjectProxy proxy = (ObjectProxy) object;
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
		for (ObjectProxy proxy : proxies) {
			try {
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
	protected void setState(State newState) throws TransactionException {
		switch (state) {
		case PREPARING:
			if (newState != State.RUNNING)
				throw new TransactionException("Invalid transaction state transition.");
			break;
		case RUNNING:
			if (newState != State.COMMITED && newState != State.ABORTED)
				throw new TransactionException("Invalid transaction state transition.");
			break;
		case COMMITED:
		case ABORTED:
			throw new TransactionException("Invalid transaction state transition.");
		}

		state = newState;
	}

	/**
	 * A comparator object for sorting remote object proxies by their IDs.
	 */
	protected Comparator<ObjectProxy> comparator = new Comparator<ObjectProxy>() {
		public int compare(ObjectProxy a, ObjectProxy b) {
			try {
				return a.getUID().compareTo(b.getUID());
			} catch (RemoteException e) {
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}

	};

	@Override
	public UUID getUID() throws RemoteException {
		return id;
	}
}
