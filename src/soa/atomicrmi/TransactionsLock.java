/*
 * Atomic RMI
 *
 * Copyright 2009-2012 Wojciech Mruczkiewicz <Wojciech.Mruczkiewicz@cs.put.poznan.pl>
 * 					   Konrad Siek <Konrad.Siek@cs.put.poznan.pl>
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

import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of a distributed JavaRMI lock. Provides functionality to
 * synchronize remote nodes. This class is used during initialization phase of
 * versioning algorithm in order to obtain the values of global counters.
 * 
 * <p>
 * Unlike the previous version this one is not strictly a singleton. It can now
 * refer to multiple remote objects located on several network nodes. More
 * importantly, initialization on a particular network node will no longer
 * prevent that node from using another transaction lock located on a different
 * different node.
 * 
 * @version 2
 * 
 * @author Wojciech Mruczkiewicz
 * @author Konrad Siek
 */
public class TransactionsLock extends UnicastRemoteObject implements ITransactionsLock {

	/**
	 * Name of this global lock that appears in JavaRMI registry.
	 */
	public static final String BIND_NAME = "soa.atomicrmi.TransactionsLock";

	/**
	 * Randomly generated serialization UID.
	 */
	private static final long serialVersionUID = 9150620278838951705L;

	/**
	 * Instances of global locks per registry.
	 */
	private static Map<Registry, ITransactionsLock> locks = new HashMap<Registry, ITransactionsLock>();

	/**
	 * Object that is used to allow only one transaction at a time while a
	 * {#link {@link TransactionsLock} lock object is retrieved from the
	 * registry. However transactions on other hosts may still do so
	 * simultaneously.
	 */
	private static Object lockRetrieveLock = new Object();

	/**
	 * Determines lock status.
	 */
	private boolean locked;

	/**
	 * Creates or retrieves an instance of a global lock. If global lock is not
	 * present in given JavaRMI registry then it is created and binded using
	 * {@link TransactionsLock#BIND_NAME} name.
	 * 
	 * @param registry
	 *            registry where an instance of the global lock should be
	 *            present.
	 * @return an instance of a global lock.
	 * @throws TransactionException
	 *             when remote execution failed or global lock could not be
	 *             accessed.
	 */
	public static ITransactionsLock getOrCreate(Registry registry) throws TransactionException {
		synchronized (lockRetrieveLock) {
			ITransactionsLock lock = locks.get(registry);
			if (lock != null)
				return lock;

			try {
				try {
					lock = (ITransactionsLock) registry.lookup(BIND_NAME);
				} catch (NotBoundException e) {
					lock = new TransactionsLock();

					try {
						registry.bind(BIND_NAME, lock);
					} catch (AlreadyBoundException e2) {
						try {
							lock = (ITransactionsLock) registry.lookup(BIND_NAME);
						} catch (NotBoundException e1) {
							throw new TransactionException("Unexpected exception obtaining global transactions lock.",
									e);
						}
					}
				}
			} catch (AccessException e) {
				throw new TransactionException("Unable to access global transactions lock.", e);
			} catch (RemoteException e) {
				throw new TransactionException("Unexpected exception obtaining global transactions lock.", e);
			}

			locks.put(registry, lock);
			return lock;
		}
	}

	/**
	 * Creates an instance of a global lock, if no instance of global lock
	 * exists at the given registry.
	 * 
	 * @param registry
	 *            registry where an instance of the global lock should be
	 *            present.
	 * @throws TransactionException
	 *             when remote execution failed or global lock could not be
	 *             accessed.
	 */
	public static void initialize(Registry registry) throws TransactionException {
		getOrCreate(registry);
	}

	/**
	 * Hides public constructor and prevents from creating an instance of this
	 * class.
	 * 
	 * @throws RemoteException
	 *             when super class constructor throws an exception.
	 */
	protected TransactionsLock() throws RemoteException {
		super();
	}

	public synchronized void lock() throws RemoteException {
		try {
			while (locked) {
				wait();
			}
		} catch (InterruptedException e) {
			throw new TransactionException("Waiting for global lock interrupted.", e);
		}

		locked = true;
	}

	public synchronized void unlock() throws RemoteException {
		locked = false;
		notify();
	}

}
