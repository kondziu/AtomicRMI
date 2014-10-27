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
import java.util.UUID;

import put.atomicrmi.Access.Mode;

/**
 * An implementation of {@link IObjectProxy} interface. It is required to
 * control remote method invocations and implement versioning algorithm.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 */
class ObjectProxy extends UnicastRemoteObject implements IObjectProxy {

	/**
	 * Copy of object for the purpose of buffering writes and reading from
	 * buffer after release.
	 */
	private Object buffer = null;
	
	/**
	 * A separate thread for performing buffering of an object that is used
	 * exclusively in read only mode. Up to one such thread can exist per object
	 * proxy.
	 * 
	 * @author Konrad Siek
	 */
	private class ReadThread extends Thread {

		private Semaphore semaphore = new Semaphore(0);
		private boolean commit;

		@Override
		public void run() {
			try {
				object.waitForCounter(px - 1); // 12
				object.transactionLock(tid);
				// we have to make a snapshot, else it thinks we didn't read the
				// object and in effect we don't get cv and rv
				snapshot = object.snapshot(); // 15
				buffer = object.clone(); // 13
				object.setCurrentVersion(px); // 14
				releaseTransaction(); // 16
			} catch (Exception e) {
				// FIXME the client-side should see the exceptions from this
				// thread.
				throw new RuntimeException(e);
			} finally {
				object.transactionUnlock(tid);
			}

			semaphore.release(1); // 17 & 18

			// dismiss
			try {
				commit = waitForSnapshot(); // 19 & 20
				// line 21 will be taken care of in wait for snapshots
				finishTransaction(commit); // 22
			} catch (RemoteException e) {
				throw new RuntimeException(e);
			}

			// semaphore.release(RELEASED);
		}
	}

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

	/**
	 * Access mode to this remote object by this transaction: read-only,
	 * write-only, etc.
	 */
	private Mode mode;

	/**
	 * Thread that performs read-only optimization, created as needed.
	 */
	private ReadThread readThread;

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
	 * @param mode
	 *            access mode (read-only, write-only, etc.)
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	ObjectProxy(ITransaction transaction, UUID tid, TransactionalUnicastRemoteObject object, long calls, Mode mode)
			throws RemoteException {
		super();
		this.transaction = transaction;
		this.object = object;
		this.tid = tid;
		this.mode = mode;

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

	public Object getWrapped(boolean useBuffer) throws RemoteException {
		if (useBuffer) {
			try {
				// this should have been handled by by preRead
				readThread.semaphore.acquire(1);
				readThread.semaphore.release(1);
				return buffer;
			} catch (Exception e) {
				e.printStackTrace();
				throw new RemoteException(e.getMessage(), e.getCause());
			}
		}
		return object;
	}

	public void startTransaction() throws RemoteException {
		TransactionFailureMonitor.getInstance().startMonitoring(this);
		px = object.startTransaction(tid);

		mv = 0;

		over = false;
	}

	/**
	 * Dear future me,
	 * 
	 * Watch out: lines 60--61 in the algorithm in the paper should 
	 * probably say:
	 * if (Cw(xT) != dom(SwT) then 
	 *     start writerelease as thread THwrx
	 * join with THwrx.
	 * 
	 * While currently preWrite is just a copy of preAny. I have not 
	 * started doing serious stuff here.
	 * 
	 * Best regards and sincere comiserations,
	 * Past me
	 */
	public boolean preWrite() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after release.");
		}

		if (mv == RELEASED || mv == ub) {
			throw new TransactionException("Upper bound is lower then number of invocations.");
		}

		if (mv == 0) {
			object.waitForCounter(px - 1);
			object.transactionLock(tid);
			/*
			 * Dear future me,
			 * 
			 * Since buffering writes assumes that at the time of performing
			 * those writes you won't attempt to pass the access condition. This
			 * means that you can't use a snapshot of the object for the buffer,
			 * since the snapshot could be a) out of date, or b) completely
			 * inconsistent (eg made in the middle of executing some method by
			 * some other transaction). Hence, you need methods of mitigating
			 * that.
			 * 
			 * One way is to use a log instead of a buffer. Whenever a method
			 * needs to be performed, store the method name, argument values,
			 * etc. in a log. Then, when the actual object should be updated
			 * from the buffer just run the methods one-by-one- from the log.
			 * Upside: fairly simple. Downside: if the transaction is simple,
			 * this yields little to none performance benefits.
			 * 
			 * Another way is to create a band new object (constructor, factory)
			 * and use that as the buffer. The methods are supposed to be all
			 * writes, so it shouldn't be a problem that the state is not the
			 * same. Then, when the object is synchronized with the buffer make
			 * a diff between a) the actual object and a brand new object, b)
			 * the brand new object and the buffer, and c) the uffer and the
			 * actual object; then use the three diffs to set the state of the
			 * actual object to include the changes from the buffer. Pro: update
			 * should be cheaper than executing log. Con: update is complex.
			 * 
			 * There's a library that can potentially facilitate this way of
			 * doing things: https://github.com/SQiShER/java-object-diff
			 * 
			 * Potentially, instead of using a brand new object, maybe a stale
			 * coherent version of the object in question could be used? Con:
			 * require a snapshot to be saved, extra overhead (how big?). Pro:
			 * quicker/simpler buffer update on average, we would do this in the
			 * future for EC implementations anyway.
			 * 
			 * By the way, as a way of optimizing writes, you can check the
			 * access condition (rather than wait for it) and if it fails, only
			 * then perform buffering. If it does not fail, perform the writes
			 * on the actual object, and maybe save some time. This can be
			 * extended to automatically switching from the buffer to the actual
			 * object as soon as the access condition turns true.
			 * 
			 * Best regards, Past me
			 */
			snapshot = object.snapshot();
		} else {
			object.transactionLock(tid);
		}

		if (snapshot.getReadVersion() != object.getCurrentVersion()) {
			object.transactionUnlockForce(tid);
			transaction.rollback();
			throw new RollbackForcedException("Rollback forced during invocation.");
		}

		mv++;

		return false;
	}

	public boolean preRead() throws RemoteException {
		if (mode == Mode.READ_ONLY) {
			// Read-only optimization (green).
			// We don't check for UB etc. because we already released this
			// object anyway, and we're using a buffer.
			try {
				// Synchronize with the Read Thread
				readThread.semaphore.acquire(1);
				readThread.semaphore.release(1);
			} catch (InterruptedException e) {
				throw new RemoteException(e.getMessage(), e.getCause());
			}

			// object.transactionLock(tid);

			mv++;

			return true;
		} else {
			// Read in a R/W object.
			return preAny();
		}
		// TODO (yellow)
		// TODO (pink)
	}

	public boolean preAny() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after release.");
		}

		if (mv == RELEASED || mv == ub) {
			throw new TransactionException("Upper bound is lower then number of invocations.");
		}

		if (mv == 0) {
			object.waitForCounter(px - 1);
			object.transactionLock(tid);
			snapshot = object.snapshot();
		} else {
			object.transactionLock(tid);
		}

		if (snapshot.getReadVersion() != object.getCurrentVersion()) {
			object.transactionUnlockForce(tid);
			transaction.rollback();
			throw new RollbackForcedException("Rollback forced during invocation.");
		}

		mv++;

		return false;
	}

	public boolean preSync(Mode accessType) throws RemoteException {
		switch (accessType) {
		case READ_ONLY:
			return preRead();
		case WRITE_ONLY:
			return preWrite();
		case ANY:
		default:
			return preAny();
		}
	}

	public void postRead() throws RemoteException {
		if (mode == Mode.READ_ONLY) {
			// if (mv == ub) {
			// object.setCurrentVersion(px);
			// releaseTransaction();
			// }
			// XXX this should be handled by read thread?

			// multiple reads should be able to coincide
			// object.transactionUnlock(tid);
		} else {
			if (over) {
				throw new TransactionException("Attempting to access transactional object after release.");
			}

			if (mv == ub) {
				object.setCurrentVersion(px);
				releaseTransaction();
			}

			object.transactionUnlock(tid);
		}
	}

	public void postAny() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after release.");
		}

		if (mv == ub) {
			object.setCurrentVersion(px);
			releaseTransaction();
		}

		object.transactionUnlock(tid);
	}

	public void postSync(Mode accessType) throws RemoteException {
		switch (accessType) {
		case READ_ONLY:
			postRead();
			break;
		case WRITE_ONLY:
		case ANY:
		default:
			postAny();
		}
	}

	public void releaseTransaction() throws RemoteException {
		if (mv != RELEASED) {
			object.releaseTransaction();
			mv = RELEASED;
		}
	}

	public void finishTransaction(boolean restore) throws RemoteException {

		if (readThread != Thread.currentThread() && mode == Mode.READ_ONLY) { // 65
			// empty
		} else {
			TransactionFailureMonitor.getInstance().stopMonitoring(this);

			object.finishTransaction(tid, snapshot, restore);

			over = true;
			snapshot = null;
		}
	}

	public boolean waitForSnapshot() throws RemoteException {
		if (readThread != Thread.currentThread() && mode == Mode.READ_ONLY) { // line
																				// 57
			try {
				readThread.join(); // line 58
			} catch (InterruptedException e) {
				throw new RemoteException(e.getMessage(), e.getCause());
			}

			return readThread.commit; // line 21
		} else {
			object.waitForSnapshot(px - 1);

			if (mv != 0 && mv != RELEASED && snapshot.getReadVersion() == object.getCurrentVersion())
				object.setCurrentVersion(px);

			releaseTransaction();

			if (snapshot == null)
				return true;

			boolean commit = snapshot.getReadVersion() <= object.getCurrentVersion();
			return commit;
		}
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
		} else {
			object.transactionLock(tid);
		}

		object.setCurrentVersion(px);
		releaseTransaction();

		object.transactionUnlock(tid);

		over = true;
		// snapshot = null;
	}

	public void lock() throws RemoteException {
		object.transactionLock(tid);
	}

	public void unlock() throws RemoteException {
		object.transactionUnlock(tid);

		// Read-only optimization.
		// This has to be lumped in here with unlock, in order to minimize the
		// number of network messages sent between transaction and proxy.
		if (mode == Mode.READ_ONLY) {
			readThread = new ReadThread();
			readThread.start();
		}
	}

	public UUID getSortingKey() throws RemoteException {
		return object.getSortingKey();
	}

	public Mode getMode() throws RemoteException {
		return mode;
	}
}
