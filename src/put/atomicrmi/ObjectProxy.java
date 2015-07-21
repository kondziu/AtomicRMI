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

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.UUID;

import put.atomicrmi.Access.Mode;
import put.atomicrmi.OneThreadToRuleThemAll.Task;

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
	protected Object buffer = null;

	private Semaphore readSemaphore = new Semaphore(0);
	private Semaphore writeSemaphore = new Semaphore(0);
	private Semaphore commitSemaphore = new Semaphore(0);

	/**
	 * State recorder for recording all changes applied to the write buffer. Can
	 * be used to apply all the changes to another instance of the same object.
	 * Used to apply those changes made in a "clean" instance of the object to a
	 * current instance.
	 */
//	protected StateRecorder writeRecorder;
	
	final protected class Invocation {
		public Invocation(Method m, Object[] args) {
			this.method = m;
			this.args = args;
		}
		final Method method;
		final Object[] args;
	}
	
	protected volatile LinkedList<Invocation> log = null;

	private boolean readCommit;
	private boolean readThreadUsed;

	private class ReadTask implements Task {
		@Override
		public boolean condition(OneThreadToRuleThemAll controller) throws TransactionException {
			// System.out.println("checksies " + px);
			return object.tryWaitForCounter(px - 1);
		}

		@Override
		public void run(OneThreadToRuleThemAll controller) throws TransactionException, CloneNotSupportedException,
				RemoteException {
			// System.out.println("ul13");
			object.transactionLock(uid);

			// we have to make a snapshot, else it thinks we didn't read the
			// object and in effect we don't get cv and rv
			snapshot = object.snapshot(); // 15
			buffer = object.clone(); // 13
			object.setCurrentVersion(px); // 14
			releaseTransaction(); // 16

			object.transactionUnlock(uid);

			readSemaphore.release(1); // 17 & 18

			controller.add(new ReadCommitTask());
		}
	}

	private class ReadCommitTask implements Task {
		@Override
		public boolean condition(OneThreadToRuleThemAll controller) throws Exception {
			return object.tryWaitForSnapshot(px - 1);
		}

		@Override
		public void run(OneThreadToRuleThemAll controller) throws Exception {
			// dismiss
			// System.out.println("Commit thread waiting for snapshots");
			readCommit = waitForSnapshot(true); // 19 & 20
			// System.out.println("Commit thread done waiting for snapshots");
			// line 21 will be taken care of in wait for snapshots
			finishTransaction(!readCommit, true); // 22
			// System.out.println("Commit thread fininished transactions");

			commitSemaphore.release(1);
		}
	}

	private class WriteTask implements Task {
		@Override
		public boolean condition(OneThreadToRuleThemAll controller) throws TransactionException {
//			System.out.println("waitsor");
			return object.tryWaitForCounter(px - 1);
		}

		@Override
		public void run(OneThreadToRuleThemAll controller) throws Exception {
			// At this point must be past object.waitForCounter(px - 1); // 24
			// System.out.println("onethread w1");
			// System.out.println("ul14");
			object.transactionLock(uid);
			// System.out.println("onethread w2");

			// Short circuit, if pre-empted.
			if (log == null) {
				object.transactionUnlock(uid);
				return;
			}

			// System.out.println("onethread w3");

			// We have to make a snapshot, else it thinks we didn't read
			// the object and in effect we don't get cv and rv.
			snapshot = object.snapshot(); // 28

			applyWriteLog(); // 24-25

			// Prevent recorder from being used again
			log = null;

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

			// System.out.println("onethread w4");

			// Release object.
			object.setCurrentVersion(px); // 27
			releaseTransaction(); // 29
			writeSemaphore.release(1);

			object.transactionUnlock(uid);

			// System.out.println("onethread w5");
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
	protected UUID uid;

	/**
	 * The wrapped remote object reference.
	 */
	protected TransactionalUnicastRemoteObject object;

	/**
	 * Snapshot of the wrapped remote object. If <code>null</code> then snapshot
	 * is not present.
	 */
	protected TransactionalUnicastRemoteObject.Snapshot snapshot;

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

	private boolean writeThreadExists;

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
	protected UUID getTransactionId() {
		return uid;
	}

	public Object getWrapped() throws RemoteException {
		return object;
	}

	public Object getBuffer() throws RemoteException {
		try {
			// this should have been handled by preRead
			if (readThreadUsed) {
				readSemaphore.acquire(1);
				readSemaphore.release(1);
			}
			
//			System.out.println("BUFFER " + buffer);

			return buffer;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RemoteException(e.getMessage(), e.getCause());
		}
	}

	public void startTransaction() throws RemoteException {
		TransactionFailureMonitor.getInstance().startMonitoring(this);
		px = object.startTransaction(uid);

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
	public BufferType preWrite() throws RemoteException {

		// System.out.println("prewrite w1");
		if (over) {
			throw new TransactionException("Attempting to access transactional object after commit.");
		}

		if (mv == RELEASED || (/* ub != 0 && mv != 0 && */mv == ub) || (mwv == wub)) {
			throw new TransactionException("Upper bound is lower then number of invocations: " + mrv + "/" + rub + " "
					+ mwv + "/" + wub + " " + mv + "/" + ub);
		}

		// System.out.println("prewrite w2");

		if (mv == 0 /* mwv == 0 && mrv == 0 */) {
			// The transaction was neither writing nor reading yet.
			// Create a buffer for writing and proceed to use the buffer.
			// System.out.println("prewrite w3a");
			// System.out.println("ul9");
			object.transactionLock(uid);
			// System.out.println("prewrite w4a");

			log = new LinkedList<Invocation>();
//			try {
//				buffer = Instrumentation.transform(object.getClass(), object, (InterceptFieldCallback) writeRecorder);
//			} catch (Exception e) {
//				e.printStackTrace();
//				throw new RemoteException(e.getLocalizedMessage(), e.getCause());
//			}

			mv++;
			mwv++;
			// System.out.println("prewrite w5a");

//			System.out.println("LOG1");
			return BufferType.LOG_ONLY; // XXX Logger

		} else if ( /* mwv > 0 && */mrv == 0) {
			// The transaction was writing before but not reading.
			// So there already exists a buffer for writing to---no need to
			// create one.
			// Use the buffer.

			// System.out.println("prewrite w3b");
			// System.out.println("ul10");
			object.transactionLock(uid);

			// System.out.println("prewrite w4b");

			mv++;
			mwv++;

//			System.out.println("LOG2");
			return BufferType.LOG_ONLY;

		} else /* if (mrv > 0) */{
			// The transaction already read from the object, so we have access
			// to it.
			// There's no need to buffer writes for now, so proceed as normal.

			// System.out.println("prewrite w3c");
			// System.out.println("ul11");
			object.transactionLock(uid);
			// System.out.println("prewrite w4c");

			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(uid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			// System.out.println("prewrite w5c");

			mv++;
			mwv++;

//			System.out.println("NON1");
			return BufferType.NONE;
		}
	}

	public void postWrite() throws RemoteException {
		// System.out.println("postwrite w1");

		if (mwv == wub || mv == ub) {

			// System.out.println("postwrite w2");
			// If mrv > 0 then there's no need for a new thread, because we
			// already have access.
			if (mrv > 0) {

				// System.out.println("postwrite w3a");
				// Transaction already has access to, because there were reads.

				// Create buffer for accessing objects after release.
				try {
					buffer = object.clone();
				} catch (CloneNotSupportedException e) {
					e.printStackTrace();
					object.transactionUnlock(uid);
					throw new RemoteException(e.getLocalizedMessage(), e.getCause());
				}

				// System.out.println("postwrite w4a");

				// Release.
				object.setCurrentVersion(px); // 27
				releaseTransaction(); // 29

			} else {
				// System.out.println("postwrite w3b");
				writeThreadExists = true;
//				System.out.println("NEW WRITE THREAD");
				OneThreadToRuleThemAll.theOneThread.add(new WriteTask());
				// System.out.println("postwrite w4b");
			}
		}

		// System.out.println("postwrite w5");
		// in all cases
		object.transactionUnlock(uid);
	}

	public BufferType preRead() throws RemoteException {

		// System.out.println("Rrr");
		if (mode == Mode.READ_ONLY) {
			// Read-only optimization (green).
			// We don't check for UB etc. because we already released this
			// object anyway, and we're using a buffer.
			try {
				// System.out.println("gimme sem");
				// Synchronize with the Read Thread
				readSemaphore.acquire(1);
				readSemaphore.release(1);
				// System.out.println("nom sem");

			} catch (InterruptedException e) {
				throw new RemoteException(e.getMessage(), e.getCause());
			}

			/**
			 * Read only mode sets mv=RELEASED in a separate thread prior to
			 * reading, so, if we increment it here, it will cause mv to be
			 * incremented AFTER it's been set to RELEASED, and cause trouble
			 * during commit. In that situation commit will think that the
			 * variable was not yet released (because mv > RELEASED) and release
			 * it again. Hillarity will then ensue.
			 */
			// mv++;
			mrv++;

//			System.out.println("BUF1");
			return BufferType.BUFFER;
		}

		if (over) {
			throw new TransactionException("Attempting to access transactional object after commit.");
		}

		// Check for exceeding the upper bounds.
		if (mrv != 0 && rub != 0 && mrv == rub || mv != 0 && ub != 0 && mv == ub) {
			throw new TransactionException("Upper bound is lower then number of invocations:" + mrv + "/" + rub + " "
					+ mwv + "/" + wub + " " + mv + "/" + ub);
		}

		// If there were previously no writes proceed as normal.
		if (mwv == 0) {
			if (over) {
				throw new TransactionException("Attempting to access transactional object after commit.");
			}

			// If there were no reads, wait for access and make snapshot.
			if (mrv == 0) {
				object.waitForCounter(px - 1);
				// System.out.println("ul4");
				object.transactionLock(uid);
				snapshot = object.snapshot();
			} else {
				// System.out.println("ul5");
				object.transactionLock(uid);
			}

			// Check for inconsistent state and rollback if necessary.
			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(uid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mrv++;
			mv++;

//			System.out.println("NON2");
			return BufferType.NONE;
		}

		// If there were reads and writes before, do the minimal "normal"
		// procedure.
		if (wub > mwv || wub == Transaction.INF) {
			if (over) {
				throw new TransactionException("Attempting to access transactional object after commit.");
			}

			if (mrv == 0) {
				// Twist! Actually there were no reads! Synchronize with write
				// buffer.
				object.waitForCounter(px - 1); // 24

				// System.out.println("ul6");
				object.transactionLock(uid);

				snapshot = object.snapshot();

				try {
					applyWriteLog(); 
				} catch (Exception e) {
					e.printStackTrace();
					throw new RemoteException(e.getLocalizedMessage(), e);
				}

				// Prevent recorder from being used again
				log = null;

				// Potentially create buffer for accessing objects after release
				// or remove buffer. Maybe?

			} else {
				// System.out.println("ul7");
				object.transactionLock(uid);
			}

			// Check for inconsistent state and rollback if necessary.
			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(uid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mrv++;
			mv++;

//			System.out.println("NON3");
			return BufferType.NONE;
		}

		// If the writes already reached their upper bound, operate on buffers.
		{
			// System.out.println("ul8");
			object.transactionLock(uid);

			// If this is the first read after write was released then
			// synchronize with the release thread, to make sure that access to
			// the object is gained and the release procedure is finished.
			if (mrv == 0) {
				try {
					// Synchronize with the Write Thread
					writeSemaphore.acquire(1);
					writeSemaphore.release(1);
				} catch (InterruptedException e) {
					// Ignore.
				}
			}

			mrv++;
			mv++;

//			System.out.println("BUF2");
			return BufferType.BUFFER;
		}
	}

	public boolean preAny() throws RemoteException {
		throw new RemoteException("Operation not supported.");
	}

	public BufferType preSync(Mode accessType) throws RemoteException {
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
		// System.out.println("postread enter");

		if (mode == Mode.READ_ONLY) {
			// Empty.
		} else {
			if (mv == ub) {
				// System.out.println("postread enter");

				object.waitForCounter(px - 1); // TODO is this necessary?
												// TODO can this cause a
												// deadlock, because waiting
												// while transaction lock is
												// acquired?
				object.setCurrentVersion(px);
				releaseTransaction();
			}

			object.transactionUnlock(uid);
		}
	}

	public void postAny() throws RemoteException {
		throw new RemoteException("Operation not supported.");
	}

	public void postSync(Mode accessType) throws RemoteException {
		switch (accessType) {
		case READ_ONLY:
			postRead();
			break;
		case WRITE_ONLY:
			postWrite();
			break;
		case ANY:
		default:
			throw new RemoteException("Illegal access type: " + accessType);
		}
	}

	public void releaseTransaction() throws RemoteException {
		if (mv != RELEASED) {
			object.releaseTransaction();
			mv = RELEASED;
		}
		OneThreadToRuleThemAll.theOneThread.ping();
	}

	public void finishTransaction(boolean restore, boolean readThread) throws RemoteException {
		if (!readThread && mode == Mode.READ_ONLY) { // 65
			// empty
		} else {
			TransactionFailureMonitor.getInstance().stopMonitoring(this);

			object.finishTransaction(uid, snapshot, restore);
			OneThreadToRuleThemAll.theOneThread.ping();

			over = true;
			snapshot = null;
		}
	}

	public boolean waitForSnapshot(boolean readThread) throws RemoteException {
		if (!readThread && mode == Mode.READ_ONLY) { // l 57
			try {
				// Synchronize with the Read Thread part 2
				commitSemaphore.acquire(1);
				commitSemaphore.release(1);
			} catch (InterruptedException e) {
				throw new RemoteException(e.getMessage(), e.getCause());
			}

			return readCommit; // line 21
		} else {
			if (writeThreadExists) {
				try {
					writeSemaphore.acquire(1);
					writeSemaphore.release(1);
				} catch (InterruptedException e) {
					// FIXME WTF? something should go here, surely
				}
			}

			synchronized (this) {
				if (log != null) {
					object.waitForCounter(px - 1); // 24
					// System.out.println("ul12");
					object.transactionLock(uid);

					// We have to make a snapshot, else it thinks we didn't read
					// the object and in effect we don't get cv and rv.
					snapshot = object.snapshot(); // 28

					try {
						applyWriteLog(); // 24-25
					} catch (Exception e) {
						e.printStackTrace();
						throw new RemoteException(e.getLocalizedMessage(), e.getCause());
					}

					// Prevent recorder from being used again.
					log = null;

					// Remove buffer.
					buffer = null;

					// Release object.
					object.setCurrentVersion(px); // 27
					releaseTransaction(); // 29

					object.transactionUnlock(uid);
				}
			}

			object.waitForSnapshot(px - 1); // FIXME it gets stuck in here.

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
	 * called by {@link TransactionFailureMonitor}. Object proxy should rollback
	 * wrapped object safely and disallow transaction commit.
	 * 
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	void OnFailure() throws RemoteException {
		System.err.println("Failure detected.");
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
			// System.out.println("ul1");
			object.transactionLock(uid);
			snapshot = object.snapshot();
		} else {
			// System.out.println("ul2");
			object.transactionLock(uid);
		}

		object.setCurrentVersion(px);
		releaseTransaction();

		object.transactionUnlock(uid);

		over = true;
		// snapshot = null;
	}

	public void lock() throws RemoteException {
		// System.out.println("ul3");
		object.transactionLock(uid);
	}

	public void unlock() throws RemoteException {
		object.transactionUnlock(uid);

		// Read-only optimization.
		// This has to be lumped in here with unlock, in order to minimize the
		// number of network messages sent between transaction and proxy.
		if (mode == Mode.READ_ONLY) {
			// readThread = new ReadThread();
			// readThread.start();
			// System.out.println("NEW READ THREAD");
			OneThreadToRuleThemAll.theOneThread.add(new ReadTask());
		}
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
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RemoteException(e.getLocalizedMessage(), e.getCause());
		} 
		log.add(new Invocation(method, args));
	}
	
	protected void applyWriteLog() throws RemoteException{
		for (Invocation invocation : log) {
			try {
				invocation.method.invoke(object, invocation.args);
			} catch (Exception e) {
				e.printStackTrace();				
			}
		}
	}

}
