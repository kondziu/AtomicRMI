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
				e.printStackTrace();
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
	 * State recorder for recording all changes applied to the write buffer. Can
	 * be used to apply all the changes to another instance of the same object.
	 * Used to apply those changes made in a "clean" instance of the object to a
	 * current instance.
	 */
	StateRecorder writeRecorder;

	private class WriteThread extends Thread {
		@Override
		public void run() {
			synchronized (ObjectProxy.this) {
				try {
					object.waitForCounter(px - 1); // 24
					object.transactionLock(tid);

					// Short circuit, if pre-empted.
					if (writeRecorder == null) {
						object.transactionUnlock(tid);
						return;
					}

					// We have to make a snapshot, else it thinks we didn't read
					// the object and in effect we don't get cv and rv.
					snapshot = object.snapshot(); // 28

					writeRecorder.applyChanges(object); // 24-25

					// Prevent recorder from being used again
					writeRecorder = null;

					// Create buffer for accessing objects after release or
					// remove buffer.
					if (rub > mrv || rub == Transaction.INF) {
						try {
							buffer = object.clone();
						} catch (CloneNotSupportedException e) {
							e.printStackTrace();
							// object.transactionUnlock(tid);
							throw new RemoteException(e.getLocalizedMessage(), e.getCause());
						}
					} else {
						// Release memory for buffer
						buffer = null;
					}

					// Release object.
					object.setCurrentVersion(px); // 27
					releaseTransaction(); // 29
				} catch (Exception e) {
					// FIXME the client-side should see the exceptions from this
					// thread.
					e.printStackTrace();
					throw new RuntimeException(e);
				} finally {
					object.transactionUnlock(tid);
				}
			}
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
	 * The minor write version counter that counts writes.
	 */
	private long mwv;

	/**
	 * The minor write version counter that counts reads.
	 */
	private long mrv;

	/**
	 * An upper bound on remote method invocations: all.
	 */
	private long ub;

	/**
	 * An upper bound on remote method invocations: writes.
	 */
	private long wub;

	/**
	 * An upper bound on remote method invocations: reads.
	 */
	private long rub;

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
	 * Thread that performs write-only asynchronous release, created as needed.
	 */
	private WriteThread writeThread;

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
	 * @param writes
	 *            an upper bound on number of remote object writes. If unknown,
	 *            no of writes should equal no of calls (worst case).
	 * @param mode
	 *            access mode (read-only, write-only, etc.)
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	ObjectProxy(ITransaction transaction, UUID tid, TransactionalUnicastRemoteObject object, long calls, long reads,
			long writes, Mode mode) throws RemoteException {
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
				if (readThread != null) {
					readThread.semaphore.acquire(1);
					readThread.semaphore.release(1);
				}
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
		mwv = 0;

		over = false;
	}

	/**
	 * Dear future me,
	 * 
	 * Watch out: lines 60--61 in the algorithm in the paper should probably
	 * say: if (Cw(xT) != dom(SwT) then start writerelease as thread THwrx join
	 * with THwrx.
	 * 
	 * While currently preWrite is just a copy of preAny. I have not started
	 * doing serious stuff here.
	 * 
	 * Best regards and sincere comiserations, Past me
	 */
	public boolean preWrite() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after release.");
		}

		if (mv == RELEASED || (ub != 0 && mv != 0 && mv == ub) || (wub != 0 && mwv != 0 && mwv == wub)) {
			throw new TransactionException("Upper bound is lower then number of invocations:" + mrv + "/" + rub + " "
					+ mwv + "/" + wub + " " + mv + "/" + ub);
		}

		if (mv == 0 /* mwv == 0 && mrv == 0 */) {

			// The transaction was neither writing nor reading yet.
			// Create a buffer for writing and proceed to use the buffer.

			object.transactionLock(tid);

			writeRecorder = new StateRecorder();
			try {
				buffer = Instrumentation.transform(object.getClass(), object, writeRecorder);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RemoteException(e.getLocalizedMessage(), e.getCause());
			}

			mv++;
			mwv++;

			return true;

		} else if ( /* mwv > 0 && */mrv == 0) {

			// The transaction was writing before but not reading.
			// So there already exists a buffer for writing to---no need to
			// create one.
			// Use the buffer.

			object.transactionLock(tid);

			mv++;
			mwv++;

			return true;

		} else /* if (mrv > 0) */{

			// The transaction already read from the object, so we have access
			// to it.
			// There's no need to buffer writes for now, so proceed as normal.

			object.transactionLock(tid);

			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(tid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mv++;
			mwv++;

			return false;
		}
	}

	public void postWrite() throws RemoteException {
		// if (writeRecorder != null && mwv == ub) {
		// writeThread = new WriteThread();
		// writeThread.start();
		// } else if (mv == ub) {
		// object.setCurrentVersion(px);
		// releaseTransaction();
		// }

		if (mwv == wub) {
			// If mrv > 0 then there's no need for a new thread, because we
			// already have access.
			if (mrv > 0) {
				// Transaction already has access to, because there were reads.
				snapshot = object.snapshot(); // 28

				// Create buffer for accessing objects after release.
				try {
					buffer = object.clone();
				} catch (CloneNotSupportedException e) {
					e.printStackTrace();
					object.transactionUnlock(tid);
					throw new RemoteException(e.getLocalizedMessage(), e.getCause());
				}

				// Release.
				object.setCurrentVersion(px); // 27
				releaseTransaction(); // 29

			} else {
				writeThread = new WriteThread();
				writeThread.start();
			}
		}

		// in all cases
		object.transactionUnlock(tid);
	}

	public boolean preRead() throws RemoteException {
		// assert((mode == Mode.READ_ONLY) == (wub == 0));

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

			mv++;
			mrv++;

			return true;
		}

		// Check for exceeding the upper bounds.
		if (mrv != 0 && rub != 0 && mrv == rub || mv != 0 && ub != 0 && mv == ub) {
			throw new TransactionException("Upper bound is lower then number of invocations:" + mrv + "/" + rub + " "
					+ mwv + "/" + wub + " " + mv + "/" + ub);
		}

		// If there were previously no writes proceed as normal.
		if (mwv == 0) {
			if (over) {
				throw new TransactionException("Attempting to access transactional object after release.");
			}

			// If there were no reads, wait for access and make snapshot.
			if (mrv == 0) {
				object.waitForCounter(px - 1);
				object.transactionLock(tid);
				snapshot = object.snapshot();
			} else {
				object.transactionLock(tid);
			}

			// Check for inconsistent state and rollback if necessary.
			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(tid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mrv++;
			mv++;

			return false;

		}

		// If there were reads and writes before, do the minimal "normal"
		// procedure.
		if (wub > mwv || wub == Transaction.INF) {
			if (over) {
				throw new TransactionException("Attempting to access transactional object after release.");
			}

			object.transactionLock(tid);

			// Check for inconsistent state and rollback if necessary.
			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(tid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mrv++;
			mv++;

			return false;
		}

		// If the writes already reached their upper bound, operate on buffers.
		{
			// If this is the first read after write was released then
			// synchronize with the release thread, to make sure that access to
			// the object is gained and the release procedure is finished.
			if (mrv == 0) {
				try {
					writeThread.join();
				} catch (InterruptedException e) {
					// Ignore.
				}
			}

			mrv++;
			mv++;

			return true;
		}

		// else {
		// throw new RemoteException("Unexpected state: " + mrv + "/" + rub +
		// " " + mwv + "/" + wub);
		// }

		// if (mwv > 0) {
		// // The transaction was writing stuff to the buffer, so we can read
		// from the buffer.
		// return true;
		// } else
		//
		// synchronized (this) {
		// // buffer related stuff goes here
		// }

		// make sure "over" is also conditional on there not being a buffer to
		// read from

		// Read in a R/W object.
		// return preAny();
		// }
		// TODO (pink)
	}

	public boolean preAny() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after release.");
		}

		if (mv == RELEASED || mv == ub) {
			throw new TransactionException("Upper bound is lower then number of invocations:" + +mrv + "/" + rub + " "
					+ mwv + "/" + wub + " " + mv + "/" + ub);
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
			throw new RemoteException("Illegal access type: " + accessType);
		}
	}

	public void postRead() throws RemoteException {
		if (mode == Mode.READ_ONLY) {
			// if (mv == ub) {
			// object.setCurrentVersion(px);
			// releaseTransaction();
			// }
			// multiple reads should be able to coincide
			// object.transactionUnlock(tid);
		} else {
			// if (over) {
			// throw new
			// TransactionException("Attempting to access transactional object after release.");
			// }

			if (mv == ub) {
				object.waitForCounter(px - 1);
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
			
			if (writeThread != null) {
				try {
					writeThread.join();
				} catch (InterruptedException e) {
				}
			}
			
			synchronized (this) {
				if (writeRecorder != null) {
					object.waitForCounter(px - 1); // 24
					object.transactionLock(tid);

					// We have to make a snapshot, else it thinks we didn't read
					// the object and in effect we don't get cv and rv.
					snapshot = object.snapshot(); // 28

					try {
						writeRecorder.applyChanges(object); // 24-25
					} catch (Exception e) {
						e.printStackTrace();
						throw new RemoteException(e.getLocalizedMessage(),e.getCause());
					}

					// Prevent recorder from being used again.
					writeRecorder = null;

					// Remove buffer.
					buffer = null;

					// Release object.
					object.setCurrentVersion(px); // 27
					releaseTransaction(); // 29
				}
			}
			
			
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
