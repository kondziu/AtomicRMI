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
package soa.atomicrmi;

import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
public class DTransaction extends UnicastRemoteObject implements ITransaction {

	/**
	 * Implementation of a heartbeater thread that is a part of failure
	 * detection mechanism. This class runs thread that sends a notification
	 * signal to every failure monitor observing this transaction. The set of
	 * failure monitors cover the set of remote object accessed by this
	 * transaction.
	 * 
	 * Notification signal is sent periodically, currently every 5s.
	 * 
	 * @author Wojciech Mruczkiewicz
	 */
	private class Heartbeat implements Runnable {

		/**
		 * Delay between notifications.
		 */
		private static final long ESTIMATED_DELAY = 5000;

		/**
		 * Set of unique identifiers of failure monitors.
		 */
		private Set<UUID> ids = new HashSet<UUID>();

		/**
		 * Collection of failure monitors that should be notified about this
		 * thread liveness.
		 */
		private List<ITransactionFailureMonitor> monitors = new ArrayList<ITransactionFailureMonitor>();

		public void run() {
			while (state != STATE_COMMITED && state != STATE_ROLLEDBACK) {
				try {
					synchronized (this) {
						this.wait(TransactionFailureMonitor.FAILURE_TIMEOUT - ESTIMATED_DELAY);
						for (ITransactionFailureMonitor monitor : monitors) {
							try {
								monitor.heartbeat(id);
							} catch (RemoteException e) {
								// Do nothing.
							}
						}
					}
				} catch (InterruptedException e) {
					// Do nothing.
				}
			}
		}

		/**
		 * Add new failure monitor that requests notifications.
		 * 
		 * @param monitor
		 *            failure monitor that will be notified.
		 * @throws RemoteException
		 *             when exception is thrown during retrieval of monitor's
		 *             unique identifier.
		 */
		public synchronized void addFailureMonitor(ITransactionFailureMonitor monitor) throws RemoteException {
			UUID id = monitor.getId();

			if (!ids.contains(id)) {
				ids.add(id);
				monitors.add(monitor);
			}
		}
	}

	/**
	 * Randomly generated serialization version UID.
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
	private int state;

	/**
	 * Randomly generated transaction unique identifier.
	 */
	private UUID id;

	/**
	 * Reference to remote global lock used only for transaction startup.
	 */
	private ITransactionsLock[] globalLocks;

	/**
	 * List of proxies of the accessed remote objects.
	 */
	private List<IObjectProxy> proxies;

	/**
	 * Heartbeater's thread reference.
	 */
	private Thread heartbeatThread;

	/**
	 * Heartbeater's instance.
	 */
	private Heartbeat heartbeat;

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
	public DTransaction(Registry... registries) throws RemoteException {
		state = STATE_PREPARING;
		id = UUID.randomUUID();

		for (int i = 0; i < registries.length; i++) {
			globalLocks[i] = TransactionsLock.getOrCreate(registries[i]);
		}

		// Sorting prevents deadlocks.
		Arrays.sort(globalLocks);

		proxies = new ArrayList<IObjectProxy>();
		heartbeat = new Heartbeat();
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
	public Object accesses(Object obj) throws TransactionException {
		return accesses(obj, INF);
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
	 * @return given remote object wrapped by special object proxy that monitors
	 *         object access.
	 * @throws TransactionException
	 *             when remote exception occurs during initialization of object
	 *             proxy.
	 */
	public Object accesses(Object obj, long calls) throws TransactionException {
		if (calls != INF && calls < 1)
			throw new TransactionException("Invalid upper bound on number of invocation.");

		if (state != STATE_PREPARING)
			throw new TransactionException("Object access information can be added only in preparation state.");

		try {
			ITransactionalRemoteObject remote = (ITransactionalRemoteObject) obj;
			IObjectProxy proxy = (IObjectProxy) remote.createProxy(this, id, calls);
			proxies.add(proxy);
			heartbeat.addFailureMonitor(remote.getFailureMonitor());
			return proxy;
		} catch (RemoteException e) {
			throw new TransactionException("Unable to create proxy for an object.", e);
		}
	}

	/**
	 * Starts and executes the transaction given by {@link Transactable}
	 * implementation. This method performs transaction initialization (obtains
	 * global locks and starts the heartbeater). It allows to call
	 * {@link DTransaction#retry()} which will cause re-execution of given
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
	public void start(DTransactable transaction) throws TransactionException {
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
				System.out.println(e.getMessage());
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
			heartbeatThread = new Thread(heartbeat);
			heartbeatThread.start();

			for (ITransactionsLock globalLock : this.globalLocks) {
				globalLock.lock();
			}

			for (IObjectProxy proxy : proxies) {
				proxy.startTransaction();
			}

			for (ITransactionsLock globalLock : this.globalLocks) {
				globalLock.unlock();
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
			finishProxies(true);
			setState(STATE_ROLLEDBACK);
			throw new RollbackForcedException("Rollback forced during commit.");
		}

		finishProxies(false);
		setState(STATE_COMMITED);

		// Signal heartbeater to stop.
		synchronized (heartbeat) {
			heartbeat.notify();
		}
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
		setState(STATE_ROLLEDBACK);

		// Signal heartbeater to stop.
		synchronized (heartbeat) {
			heartbeat.notify();
		}
	}

	/**
	 * Rolls-back all the changes made by this transaction and re-executes it.
	 * This method can be called only when transaction was started using the
	 * {@link DTransaction#start(Transactable)} method.
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
	private boolean waitForSnapshots() {
		boolean commit = true;

		for (IObjectProxy proxy : proxies) {
			try {
				if (!proxy.waitForSnapshot())
					commit = false;
			} catch (RemoteException e) {
				commit = false;
			}
		}

		return commit;
	}

	/**
	 * Finalizes and releases all remote objects accessed by this transaction.
	 * 
	 * @param restore
	 *            determines if changes made by this transaction should be
	 *            restored or commited.
	 */
	private void finishProxies(boolean restore) {
		for (IObjectProxy proxy : proxies) {
			try {
				proxy.finishTransaction(restore);
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
	private void setState(int newState) throws TransactionException {
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
}
