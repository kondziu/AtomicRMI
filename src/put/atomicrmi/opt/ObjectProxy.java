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
package put.atomicrmi.opt;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

import put.atomicrmi.opt.Transaction.AccessType;

/**
 * An implementation of {@link IObjectProxy} interface. It is required to
 * control remote method invocations and implement versioning algorithm.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 */
class ObjectProxy extends UnicastRemoteObject implements IObjectProxy {

	/**
	 * Randomly generated serialization UID.
	 */
	private static final long serialVersionUID = -5524954471581314314L;

	/**
	 * The version counter value that determines when counter is released.
	 */
	private static final long RELEASED = -1;

	/**
	 * Remote reference to the transaction that this proxy is working for.
	 */
	private ITransaction transaction;

	/**
	 * Transaction unique identifier.
	 */
	private UUID tid;

	/**
	 * The wrapped remote object reference.
	 */
	private TransactionalUnicastRemoteObject object;

	/**
	 * Snapshot of the wrapped remote object. If <code>null</code> then snapshot
	 * is not present.
	 */
	private TransactionalUnicastRemoteObject.Snapshot snapshot;

	/**
	 * Determines if transaction is finished.
	 */
	private boolean over = false;

	/**
	 * The value of private counter for this remote object.
	 */
	private long px;

	/**
	 * The minor version counter that counts remote method invocations.
	 */
	private long mv;

	/**
	 * An upper bound on remote method invocations.
	 */
	private long ub;

	private AccessType accessType;

	/**
	 * Creates the object proxy for given remote object.
	 * 
	 * @param transaction
	 *            transaction the proxy is created for.
	 * @param tid
	 *            transaction unique identifier.
	 * @param object
	 *            remote object that is being wrapped.
	 * @param calls
	 *            an upper bound on number of remote object invocations.
	 * @param type
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	ObjectProxy(ITransaction transaction, UUID tid, TransactionalUnicastRemoteObject object, long calls, AccessType type)
			throws RemoteException {
		super();
		this.transaction = transaction;
		this.object = object;
		this.tid = tid;

		accessType = type;
		ub = calls;

		over = true;
	}

	/**
	 * Retrieves the transaction unique identifier that identifies transaction
	 * that this remote object proxy belongs to.
	 * 
	 * @return transaction unique identifier.
	 */
	UUID getTransactionId() {
		return tid;
	}

	public ITransactionalRemoteObject getWrapped() {
		return object;
	}

	public void startTransaction() throws RemoteException {
		TransactionFailureMonitor.getInstance().startMonitoring(this);
		px = object.startTransaction(tid);

		mv = 0;

		over = false;
		
		// Read-only optimization: this in a separate thread "store"
//		if (accessType == AccessType.READ) {
//			object.waitForCounter(px - 1);
//			object.transactionLock(tid);
//			buffer = magic(object.snapshot());
//
//			if (over)
//				return;
//
//			object.setCurrentVersion(px);
//			releaseTransaction();
//
//			object.transactionUnlock(tid);
//			
//			stored.notify();
//					
//			/* TODO like on commit */
//		}
	}
	
	

	public void preSync() throws RemoteException {
		if (over)
			throw new TransactionException("Attempting to access transactional object after release.");

		if (mv == RELEASED || mv == ub)
			throw new TransactionException("Upper bound is lower then number of invocations.");

		if (mv == 0) {
			object.waitForCounter(px - 1);
			object.transactionLock(tid);
			snapshot = object.snapshot();
		} else
			object.transactionLock(tid);

		if (snapshot.getReadVersion() != object.getCurrentVersion()) {
			transaction.rollback();
			throw new RollbackForcedException("Rollback forced during invocation.");
		}

		mv++;

	}

	public void postSync() throws RemoteException {
		if (over)
			throw new TransactionException("Attempting to access transactional object after release.");
			//return;

		if (mv == ub) {
			object.setCurrentVersion(px);
			releaseTransaction();
		}

		object.transactionUnlock(tid);
	}

	public void releaseTransaction() throws RemoteException {
		if (mv != RELEASED) {
			object.releaseTransaction();
			mv = RELEASED;
		}
	}

	public void finishTransaction(boolean restore) throws RemoteException {
		TransactionFailureMonitor.getInstance().stopMonitoring(this);

		object.finishTransaction(tid, snapshot, restore);

		over = true;
		snapshot = null;
	}

	public boolean waitForSnapshot() throws RemoteException {
		object.waitForSnapshot(px - 1);

		if (mv != 0 && mv != RELEASED && snapshot.getReadVersion() == object.getCurrentVersion())
			object.setCurrentVersion(px);

		releaseTransaction();

		if (snapshot == null)
			return true;

		return snapshot.getReadVersion() <= object.getCurrentVersion();
	}

	/**
	 * Notification that transaction failure has been detected. This method is
	 * called by {@link TransactionFailureMonitor}. Object proxy should rollback
	 * wrapped object safely and disallow transaction commit.
	 * 
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	void OnFailure() throws RemoteException {
		System.out.println("Failure detected.");
		waitForSnapshot();
		finishTransaction(true);
	}

	/**
	 * Declare that the object will no longer be used by the current transaction
	 * and allow it to be used by other transactions.
	 * 
	 * @author K. Siek
	 * @throws RemoteException
	 */
	public void free() throws RemoteException {
		if (mv == 0) {
			object.waitForCounter(px - 1);
			object.transactionLock(tid);
			snapshot = object.snapshot();
		} else
			object.transactionLock(tid);
		
		object.setCurrentVersion(px);
		releaseTransaction();

		object.transactionUnlock(tid);

		over = true;
		//snapshot = null;
	}

	@Override
	public void lock() throws RemoteException {
		object.transactionLock(tid);
	}

	@Override
	public void unlock() throws RemoteException {
		object.transactionUnlock(tid);
	}

	@Override
	public UUID getSortingKey() throws RemoteException {
		return object.getSortingKey();
	}

	public AccessType getAccessType() {
		return accessType;
	}
}
