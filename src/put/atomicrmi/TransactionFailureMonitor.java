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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detects failure of transactions that locked some remote object on particular
 * node. There is a single instance of this worker thread per node in
 * distributed system. Worker thread is a daemon and an instance of this class
 * is a singleton.
 * 
 * @author Wojciech Mruczkiewicz
 */
public class TransactionFailureMonitor extends UnicastRemoteObject implements Runnable, ITransactionFailureMonitor {

	/**
	 * Time between checking for failed transactions.
	 */
	static final long FAILURE_TIMEOUT = 15000; // 15s

	/**
	 * Randomly generated serialization UID.
	 */
	private static final long serialVersionUID = -5145629835664751876L;

	/**
	 * An instance of a transactions failure monitor.
	 */
	private static TransactionFailureMonitor monitor;

	/**
	 * Worker thread handle.
	 */
	private Thread monitorThread;

	/**
	 * Mapping between transaction and object proxy placed on particular node.
	 */
	private Map<UUID, Set<ObjectProxy>> proxies;

	/**
	 * This map determines if transaction generated heart beat since last check.
	 */
	private Map<UUID, BooleanHolder> alive;

	/**
	 * Transactions failure monitor unique identifier.
	 */
	private UUID id = UUID.randomUUID();

	private boolean shutdown;

	/**
	 * Gives the instance to transaction failures monitor. Returned value is
	 * unique for a node in distributed system.
	 * 
	 * @return an instance of a transaction failure monitor.
	 * @throws RemoteException
	 */
	synchronized public static TransactionFailureMonitor getInstance() throws RemoteException {
		if (monitor == null)
			monitor = new TransactionFailureMonitor();
		return monitor;
	}

	/**
	 * Creates an instance of transaction failure monitor and starts worker
	 * thread.
	 * 
	 * @throws RemoteException
	 *             when super class constructor throws an exception.
	 */
	protected TransactionFailureMonitor() throws RemoteException {
		super();

		proxies = new HashMap<UUID, Set<ObjectProxy>>();
		alive = new HashMap<UUID, BooleanHolder>();

		monitorThread = new Thread(this, "ARMI FailMon " + id);
		monitorThread.setDaemon(true);
		monitorThread.start();
	}

	public UUID getId() throws RemoteException {
		return id;
	}

	public synchronized void heartbeat(UUID tid) throws RemoteException {
		BooleanHolder holder = alive.get(tid);
		if (holder != null)
			holder.value = true;
	}

	/**
	 * Starts to monitor liveness of given proxy transaction.
	 * 
	 * @param proxy
	 *            proxy which transaction should be monitored.
	 */
	synchronized void startMonitoring(ObjectProxy proxy) {
		UUID tid = proxy.getTransactionId();

		if (!proxies.containsKey(tid)) {
			proxies.put(tid, new HashSet<ObjectProxy>());
			alive.put(tid, new BooleanHolder(true));
		}

		proxies.get(tid).add(proxy);
	}

	/**
	 * Stops monitoring liveness of given proxy transaction.
	 * 
	 * @param proxy
	 *            proxy for which transaction monitoring should be stopped.
	 */
	synchronized void stopMonitoring(ObjectProxy proxy) {
		UUID tid = proxy.getTransactionId();
		Set<ObjectProxy> set = proxies.get(tid);

		if (set != null) {
			set.remove(proxy);
			if (set.size() == 0) {
				proxies.remove(tid);
				alive.remove(tid);
			}
		}
	}

	/**
	 * Stop the failure detector due to an emergency: interrupt the monitor
	 * thread and make it exit.
	 * 
	 * @author Konrad Siek
	 */
	synchronized public void emergencyStop() {
		if (!monitorThread.isAlive()) {
			return;
		}
		shutdown = true;
		monitorThread.interrupt();
	}

	/**
	 * Failure detection worker thread loop. It is closed when application
	 * quits.
	 */
	public void run() {
		Set<ObjectProxy> failures = new HashSet<ObjectProxy>();

		while (true) {
			try {
				Thread.sleep(FAILURE_TIMEOUT);

				synchronized (this) {
					for (UUID tid : alive.keySet()) {
						BooleanHolder holder = alive.get(tid);
						if (!holder.value)
							failures.addAll(proxies.get(tid));
						holder.value = false;
					}
				}

				for (ObjectProxy proxy : failures)
					proxy.OnFailure();
			} catch (InterruptedException e) {
				if (shutdown) {
					shutdown = false;
					return;
				}
			} catch (RemoteException e) {
				throw new RuntimeException("Unexpected error in failure detector.");
			}
		}
	}
}
