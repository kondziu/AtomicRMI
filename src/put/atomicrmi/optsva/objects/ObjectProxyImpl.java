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
package put.atomicrmi.optsva.objects;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import put.atomicrmi.optsva.Access.Mode;
import put.atomicrmi.optsva.RollbackForcedException;
import put.atomicrmi.optsva.Transaction;
import put.atomicrmi.optsva.TransactionException;
import put.atomicrmi.optsva.TransactionRef;
import put.atomicrmi.optsva.sync.TaskController;
import put.atomicrmi.optsva.sync.TaskController.Task;
import put.atomicrmi.optsva.sync.TransactionFailureMonitorImpl;

/**
 * An implementation of {@link ObjectProxy} interface. It is required to
 * control remote method invocations and implement versioning algorithm.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 */
public class ObjectProxyImpl extends UnicastRemoteObject implements ObjectProxy {

	/**
	 * A semaphore that is closed until read buffering is completed.
	 */
	private transient CountDownLatch readSemaphore = new CountDownLatch(1);

	/**
	 * A semaphore that is closed until write buffering is completed.
	 */
	private transient CountDownLatch writeSemaphore = new CountDownLatch(1);

	/**
	 * A semaphore that is closed until separate of committing of a read-only
	 * object is done.
	 */
	private transient CountDownLatch commitSemaphore = new CountDownLatch(1);

	/**
	 * A structure for holding information about executed methods for the
	 * purposes of buffering/logging.
	 * 
	 * @author Konrad Siek
	 */
	final protected class Invocation {
		public Invocation(Method m, Object[] args) {
			this.method = m;
			this.args = args;
		}

		final Method method;
		final Object[] args;
	}

	/**
	 * Separate read thread task: buffers a read-only object and releases it.
	 * Also starts a new thread to commit that object as soon as possible.
	 * 
	 * @author Konrad Siek
	 * 
	 */
	private class BufferReadOnly implements Task {
		@Override
		public boolean condition(TaskController controller) throws TransactionException {
			/** Wait on access condition */
			return object.tryWaitForCounter(px - 1);
		}

		@Override
		public void run(TaskController controller) throws TransactionException, CloneNotSupportedException,
				RemoteException {

			object.transactionLock(uid);

			/**
			 * Copy the object to buffer.
			 */
			snapshot = object.snapshot();
			copyBuffer = object.clone();
			object.setCurrentVersion(px);

			/**
			 * Release the object.
			 */
			releaseTransaction();

			object.transactionUnlock(uid);

			// TODO there must be something better == more lightweight than
			// semaphores for this
			readSemaphore.countDown();

			/**
			 * Start early commitment for the object.
			 */
			controller.add(new CommitReadOnlyEarly());
		}
	}

	/**
	 * Commit a single shared read-only object after reading it in a separate
	 * thread.
	 * 
	 * @author Konrad Siek
	 */
	private class CommitReadOnlyEarly implements Task {
		@Override
		public boolean condition(TaskController controller) throws Exception {
			/** Wait on commit condition */
			return object.tryWaitForSnapshot(px - 1);
		}

		@Override
		public void run(TaskController controller) throws Exception {
			readOnlyEarlyCommitSuccesful = waitForSnapshot(true);
			finishTransaction(!readOnlyEarlyCommitSuccesful, true);

			commitSemaphore.countDown();
		}
	}

	/**
	 * Apply writes that were performed pre-synchronization and logged in the
	 * buffer.
	 * 
	 * @author Konrad Siek
	 */
	private class ApplyLogBuffer implements Task {
		@Override
		public boolean condition(TaskController controller) throws TransactionException {
			/** Wait on commit condition */
			return object.tryWaitForCounter(px - 1);
		}

		@Override
		public void run(TaskController controller) throws Exception {
			object.transactionLock(uid);

			/** Short circuit, if pre-empted. */
			if (logBuffer == null) {
				object.transactionUnlock(uid);
				return;
			}

			/**
			 * We have to make a snapshot, else it thinks we didn't read the
			 * object and in effect we don't get cv and rv.
			 */
			// TODO maybe we can have a fake snapshot instead, with only cv and
			// rv, but no data?
			snapshot = object.snapshot();

			/** Apply logged method requests, if any. */
			applyWriteLog();

			/** Prevent recorder from being used again */
			logBuffer = null;

			/**
			 * Create buffer for accessing objects after release or remove
			 * buffer.
			 */
			if (rub > mrv || rub == Transaction.INF) {
				try {
					copyBuffer = object.clone();
				} catch (CloneNotSupportedException e) {
					e.printStackTrace();
					throw new RemoteException(e.getLocalizedMessage(), e.getCause());
				}
			} else {
				/** Release memory for buffer. */
				copyBuffer = null;
			}

			/** Release object. */
			object.setCurrentVersion(px);
			releaseTransaction();

			writeSemaphore.countDown();

			object.transactionUnlock(uid);
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
	 * Log buffer containing method call specification for application when the
	 * object is synchronized.
	 */
	protected LinkedList<Invocation> logBuffer = null;

	/**
	 * Copy of object for the purpose of buffering writes and reading from
	 * buffer after release.
	 */
	protected Object copyBuffer = null;

	/**
	 * Value returned by a commit done by a separate read thread. If this is
	 * <code>false</code> after the thread is finished, we need to abort.
	 */
	private boolean readOnlyEarlyCommitSuccesful;

	/**
	 * True if a separate read thread was used.
	 */
	private boolean readOnlyThreadUsed;

	/**
	 * True if a separate write thread was used.
	 */
	private boolean writeSynchronizationThreadExists;

	/**
	 * Remote reference to the transaction that this proxy is working for.
	 */
	private final TransactionRef transaction;

	/**
	 * Transaction unique identifier.
	 */
	protected final UUID uid;

	/**
	 * The wrapped remote object reference.
	 */
	protected final TransactionalUnicastRemoteObject object;

	/**
	 * Snapshot of the wrapped remote object. If <code>null</code> then snapshot
	 * is not present.
	 */
	protected Snapshot snapshot;

	/**
	 * Determines if transaction is finished.
	 */
	protected boolean over = false;

	/**
	 * The value of private counter for this remote object.
	 */
	protected long px;

	/**
	 * The minor version counter that counts remote method invocations.
	 */
	protected long mv;

	/**
	 * The minor write version counter that counts writes.
	 */
	protected long mwv;

	/**
	 * The minor write version counter that counts reads.
	 */
	private long mrv;

	/**
	 * An upper bound on remote method invocations: all.
	 */
	private final long ub;

	/**
	 * An upper bound on remote method invocations: writes.
	 */
	private final long wub;

	/**
	 * An upper bound on remote method invocations: reads.
	 */
	private final long rub;

	/**
	 * Access mode to this remote object by this transaction: read-only,
	 * write-only, etc.
	 */
	private final Mode mode;

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
	public ObjectProxyImpl(TransactionRef transaction, UUID tid, TransactionalUnicastRemoteObject object, long calls, long reads,
			long writes, Mode mode) throws RemoteException {

		super();

		this.transaction = transaction;
		this.object = object;
		this.uid = tid;
		this.mode = mode;

		ub = calls;
		wub = writes;
		rub = reads;
		over = true;
	}

	/**
	 * Retrieves the transaction unique identifier that identifies transaction
	 * that this remote object proxy belongs to.
	 * 
	 * @return transaction unique identifier.
	 */
	public UUID getTransactionId() {
		return uid;
	}

	/**
	 * Get the actual object for which this is a proxy.
	 */
	public Object getWrapped() throws RemoteException {
		return object;
	}

	/**
	 * Get a read-only buffer.
	 */
	public Object getBuffer() throws RemoteException {
		try {
			// XXX This should have been handled by preRead
			if (readOnlyThreadUsed) {
				readSemaphore.await();
			}

			return copyBuffer;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RemoteException(e.getMessage(), e.getCause());
		}
	}

	public void startTransaction() throws RemoteException {
		TransactionFailureMonitorImpl.getInstance().startMonitoring(this);
		px = object.startTransaction(uid);

		mv = 0;
		mwv = 0;

		over = false;
	}

	/*
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


	public BufferType preWrite() throws RemoteException {

		if (over) {
			throw new TransactionException("Attempting to access transactional object after commit.");
		}

		if (mv == RELEASED || (mv == ub) || (mwv == wub)) {
			throw new TransactionException("Upper bound is lower then number of invocations: " + mrv + "/" + rub + " "
					+ mwv + "/" + wub + " " + mv + "/" + ub);
		}

		object.transactionLock(uid);

		if (mv == 0) {

			/**
			 * The transaction was neither writing nor reading yet.
			 */
			logBuffer = new LinkedList<Invocation>();

			mv++;
			mwv++;

			return BufferType.LOG_BUFFER;

		} else if (mrv == 0) {

			/**
			 * The transaction was writing before but not reading. So there
			 * already exists a buffer for writing to---no need to create one.
			 */
			mv++;
			mwv++;

			return BufferType.LOG_BUFFER;

		} else /* if (mrv > 0) */{

			/**
			 * The transaction already read from the object, so we have access
			 * to it. There's no need to buffer writes for now, so proceed as
			 * normal.
			 */
			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(uid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mv++;
			mwv++;

			return BufferType.NONE;
		}
	}

	public void postWrite() throws RemoteException {

		if (mwv == wub || mv == ub) {

			/**
			 * If mrv > 0 then there's no need for a new thread, because we
			 * already have access.
			 */

			if (mrv > 0) {

				/**
				 * Transaction already has access to object, because there were
				 * reads. Create buffer for accessing objects after release.
				 */
				try {
					copyBuffer = object.clone();
				} catch (CloneNotSupportedException e) {
					e.printStackTrace();
					object.transactionUnlock(uid);
					throw new RemoteException(e.getLocalizedMessage(), e.getCause());
				}

				/** Release. */
				object.setCurrentVersion(px);
				releaseTransaction();

			} else {

				/**
				 * Transaction already has no access to object. Let a separate
				 * thread take car of updating the state after we have access.
				 */
				writeSynchronizationThreadExists = true;
				TaskController.theOneThread.add(new ApplyLogBuffer());
			}
		}

		object.transactionUnlock(uid);
	}

	public BufferType preRead() throws RemoteException {

		if (mode == Mode.READ_ONLY) {
			/**
			 * Read-only optimization (green). We don't check for UB etc.
			 * because we already released this object anyway, and we're using a
			 * buffer.
			 */
			try {
				/** Synchronize with the Read Thread */
				readSemaphore.await();
			} catch (InterruptedException e) {
				throw new RemoteException(e.getMessage(), e.getCause());
			}

			/**
			 * Read only mode sets mv=RELEASED in a separate thread prior to
			 * reading, so, if we increment it here, it will cause mv to be
			 * incremented AFTER it's been set to RELEASED, and cause trouble
			 * during commit. In that situation commit will think that the
			 * variable was not yet released (because mv > RELEASED) and release
			 * it again. Hilarity will then ensue.
			 */
			mrv++;
			// mv++;

			return BufferType.COPY_BUFFER;
		}

		if (over) {
			throw new TransactionException("Attempting to access transactional object after commit.");
		}

		/** Check for exceeding the upper bounds. */
		if (mrv != 0 && rub != 0 && mrv == rub || mv != 0 && ub != 0 && mv == ub) {
			throw new TransactionException("Upper bound is lower then number of invocations:" + mrv + "/" + rub + " "
					+ mwv + "/" + wub + " " + mv + "/" + ub);
		}

		/** If there were previously no writes proceed as normal. */
		if (mwv == 0) {
			if (over) {
				throw new TransactionException("Attempting to access transactional object after commit.");
			}

			/** If there were no reads, wait for access and make snapshot. */
			if (mrv == 0) {
				object.waitForCounter(px - 1);
				object.transactionLock(uid);
				snapshot = object.snapshot();
			} else {
				object.transactionLock(uid);
			}

			/** Check for inconsistent state and abort if necessary. */
			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(uid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mrv++;
			mv++;

			return BufferType.NONE;
		}

		/**
		 * If there were reads and writes before, do the minimal "normal"
		 * procedure.
		 */
		if (wub > mwv || wub == Transaction.INF) {
			if (over) {
				throw new TransactionException("Attempting to access transactional object after commit.");
			}

			if (mrv == 0) {
				/**
				 * Twist! Actually there were no reads! Synchronize with write
				 * buffer.
				 */
				object.waitForCounter(px - 1);
				object.transactionLock(uid);
				snapshot = object.snapshot();

				try {
					applyWriteLog();
				} catch (Exception e) {
					e.printStackTrace();
					throw new RemoteException(e.getLocalizedMessage(), e);
				}

				/** Prevent recorder from being used again. */
				logBuffer = null;

				// XXX Potentially create buffer for accessing objects after
				// release or remove buffer. Maybe?

			} else {
				object.transactionLock(uid);
			}

			/** Check for inconsistent state and abort if necessary. */
			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(uid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mrv++;
			mv++;

			return BufferType.NONE;
		}

		/** If the writes already reached their upper bound, operate on buffers. */
		{
			object.transactionLock(uid);

			/**
			 * If this is the first read after write was released then
			 * synchronize with the release thread, to make sure that access to
			 * the object is gained and the release procedure is finished.
			 */
			if (mrv == 0) {
				try {
					/** Synchronize with the Write Thread */
					writeSemaphore.await();
				} catch (InterruptedException e) {
					// Ignore.
				}
			}

			mrv++;
			mv++;

			return BufferType.COPY_BUFFER;
		}
	}

	public void postRead() throws RemoteException {
		if (mode == Mode.READ_ONLY) {
			// Empty.
		} else {
			if (mv == ub) {
				// TODO is this necessary?
				// TODO waiting while in transaction lock - can cause deadlock?
				object.waitForCounter(px - 1);
				object.setCurrentVersion(px);
				releaseTransaction();
			}
			object.transactionUnlock(uid);
		}
	}

	public void releaseTransaction() throws RemoteException {
		if (mv != RELEASED) {
			object.releaseTransaction();
			mv = RELEASED;
		}
		
		TaskController.theOneThread.ping();
	}

	public void finishTransaction(boolean restore, boolean readThread) throws RemoteException {
		if (!readThread && mode == Mode.READ_ONLY) { 
			/**
			 * Nothing happens here, since everything is handled by a separate
			 * thread.
			 */

		} else {
			TransactionFailureMonitorImpl.getInstance().stopMonitoring(this);

			object.finishTransaction(uid, snapshot, restore);
			TaskController.theOneThread.ping();

			over = true;
			snapshot = null;
		}
	}

	public boolean waitForSnapshot(boolean readThread) throws RemoteException {

		if (!readThread && mode == Mode.READ_ONLY) {
			try {
				/** Synchronize with the Read Thread */
				commitSemaphore.await();
			} catch (InterruptedException e) {
				throw new RemoteException(e.getMessage(), e.getCause());
			}

			return readOnlyEarlyCommitSuccesful;

		} else {

			if (writeSynchronizationThreadExists) {
				try {
					writeSemaphore.await();
				} catch (InterruptedException e) {
					// Intentionally left blank.
				}
			}

			synchronized (this) {
				if (logBuffer != null) {
					object.waitForCounter(px - 1);

					object.transactionLock(uid);

					/**
					 * We have to make a snapshot, else it thinks we didn't read
					 * the object and in effect we don't get cv and rv.
					 */
					snapshot = object.snapshot();

					try {
						applyWriteLog();
					} catch (Exception e) {
						e.printStackTrace();
						throw new RemoteException(e.getLocalizedMessage(), e.getCause());
					}

					/** Prevent recorder from being used again. */
					logBuffer = null;

					/** Remove buffer. */
					copyBuffer = null;

					/** Release object. */
					object.setCurrentVersion(px);
					releaseTransaction();

					object.transactionUnlock(uid);
				}
			}

			object.waitForSnapshot(px - 1);

			if (mv != 0 && mv != RELEASED && snapshot.getReadVersion() == object.getCurrentVersion())
				object.setCurrentVersion(px);

			if (!readThread)
				releaseTransaction();

			if (snapshot == null)
				return true;

			boolean commit = snapshot.getReadVersion() <= object.getCurrentVersion();

			return commit;
		}
	}

	/**
	 * Notification that transaction failure has been detected. This method is
	 * called by {@link TransactionFailureMonitorImpl}. Object proxy should abort
	 * wrapped object safely and disallow transaction commit.
	 * 
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	public void onFailure() throws RemoteException {
		System.err.println("Failure detected.");

		// TODO make a fully fault tolerant variant
		waitForSnapshot(false);
		finishTransaction(true, false);
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
			object.transactionLock(uid);
			snapshot = object.snapshot();
		} else
			object.transactionLock(uid);

		object.setCurrentVersion(px);
		releaseTransaction();

		object.transactionUnlock(uid);

		over = true;
	}

	public void lock() throws RemoteException {
		object.transactionLock(uid);
	}

	public void unlock() throws RemoteException {
		object.transactionUnlock(uid);

		/**
		 * Read-only optimization. This has to be lumped in here with unlock, in
		 * order to minimize the number of network messages sent between
		 * transaction and proxy.
		 */
		if (mode == Mode.READ_ONLY)
			TaskController.theOneThread.add(new BufferReadOnly());
	}

	public UUID getUID() throws RemoteException {
		return object.getUID();
	}

	public Mode getMode() throws RemoteException {
		return mode;
	}

	public void update() throws RemoteException {
		throw new RemoteException("Invalid operation type for general purpose proxy: update.");
	}

	public void log(String methodName, Class<?>[] argTypes, Object[] args) throws RemoteException {
		Method method;
		try {
			method = object.getClass().getMethod(methodName, argTypes);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RemoteException(e.getLocalizedMessage(), e.getCause());
		}
		logBuffer.add(new Invocation(method, args));
	}

	protected void applyWriteLog() throws RemoteException {
		for (Invocation invocation : logBuffer) {
			try {
				invocation.method.invoke(object, invocation.args);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
