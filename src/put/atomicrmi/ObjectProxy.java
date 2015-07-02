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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
	StateRecorder writeRecorder;

	private boolean readCommit;
	private boolean readThreadUsed;

	static final private class SynchThread extends Thread {
		static private SynchThread evilSingleton = null;

		final List<ObjectProxy> writers = new LinkedList<ObjectProxy>();
		final List<ObjectProxy> readers = new LinkedList<ObjectProxy>();
		final List<ObjectProxy> committers = new LinkedList<ObjectProxy>();

		final List<ObjectProxy> writersQ = new LinkedList<ObjectProxy>();
		final List<ObjectProxy> readersQ = new LinkedList<ObjectProxy>();
		final List<ObjectProxy> committersQ = new LinkedList<ObjectProxy>();

		private SynchThread() {
			super("SynchThread");
		}

		public static final SynchThread get() {
			if (evilSingleton == null) {
				evilSingleton = new SynchThread();
				evilSingleton.start();
			}
			return evilSingleton;
		}

		public synchronized void addWriter(ObjectProxy proxy) {
			writersQ.add(proxy);
		}

		public synchronized void addReader(ObjectProxy proxy) {
			readersQ.add(proxy);
		}

		public synchronized void addCommitters(ObjectProxy proxy) {
			committersQ.add(proxy);
		}

		public void run() {

			while (true) {
				
				try {
					Thread.sleep(2);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}

				// FIXME streamline list usage here

				synchronized (this) {
					readers.addAll(readersQ);
					readersQ.clear();
				}

				Iterator<ObjectProxy> readerIter = readers.iterator();
				while (readerIter.hasNext()) {
					ObjectProxy proxy = readerIter.next();
					synchronized (ObjectProxy.class) {
						try {
							boolean acquired = proxy.object.tryWaitForCounter(proxy.px - 1);
							if (acquired) {
								proxy.readThreadStuff();
								addCommitters(proxy);
								readerIter.remove();
							}

						} catch (Exception e) {
							e.printStackTrace();
							throw new RuntimeException("SynchThread: " + e.getMessage(), e.getCause());
						}
					}
				}

				synchronized (this) {
					writers.addAll(writersQ);
					writersQ.clear();
				}

				Iterator<ObjectProxy> writerIter = writers.iterator();
				while (writerIter.hasNext()) {
					ObjectProxy proxy = writerIter.next();
					synchronized (ObjectProxy.class) {
						try {
							boolean acquired = proxy.object.tryWaitForCounter(proxy.px - 1);
							if (acquired) {
								proxy.writeThreadStuff();
								writerIter.remove();
							}

						} catch (Exception e) {
							throw new RuntimeException("SynchThread: " + e.getLocalizedMessage(), e.getCause());
						}
					}
				}

				synchronized (this) {
					committers.addAll(committersQ);
					committersQ.clear();
				}

				Iterator<ObjectProxy> commitIter = committers.iterator();
				while (commitIter.hasNext()) {
					ObjectProxy proxy = commitIter.next();
					synchronized (ObjectProxy.class) {
						try {
							boolean acquired = proxy.object.tryWaitForSnapshot(proxy.px - 1);
							if (acquired) {
								proxy.commitThreadStuff();
								commitIter.remove();
							}

						} catch (Exception e) {
							throw new RuntimeException("SynchThread: " + e.getLocalizedMessage(), e.getCause());
						}
					}
				}

			}
		}
	}

	void readThreadStuff() {
		try {
			object.transactionLock(uid);
			
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
			object.transactionUnlock(uid);
		}

		readSemaphore.release(1); // 17 & 18		
	}

	void commitThreadStuff() {
		// dismiss
		try {
			boolean commit = waitForSnapshot(true); // 19 & 20
			// line 21 will be taken care of in wait for snapshots
			finishTransaction(!commit, true); // 22

			this.readCommit = commit;
		} catch (RemoteException e) {
			throw new RuntimeException(e);
		}

		commitSemaphore.release(1);
	}

	void writeThreadStuff() {
		// At this point must be past object.waitForCounter(px - 1); // 24
		try {
			object.transactionLock(uid);

			// Short circuit, if pre-empted.
			if (writeRecorder == null) {
				object.transactionUnlock(uid);
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
			writeSemaphore.release(1);

		} catch (Exception e) {
			// FIXME the client-side should see the exceptions from this
			// thread.
			e.printStackTrace();
			throw new RuntimeException(e);
		} finally {
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

	public Object getWrapped(boolean useBuffer) throws RemoteException {
		if (useBuffer) {
			try {
				// this should have been handled by preRead
				if (readThreadUsed) {
					readSemaphore.acquire(1);
					readSemaphore.release(1);
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
	public boolean preWrite() throws RemoteException {
		if (over) {
			throw new TransactionException("Attempting to access transactional object after commit.");
		}

		if (mv == RELEASED || (/* ub != 0 && mv != 0 && */mv == ub) || (mwv == wub)) {
			throw new TransactionException("Upper bound is lower then number of invocations: " + mrv + "/" + rub + " "
					+ mwv + "/" + wub + " " + mv + "/" + ub);
		}

		if (mv == 0 /* mwv == 0 && mrv == 0 */) {
			// The transaction was neither writing nor reading yet.
			// Create a buffer for writing and proceed to use the buffer.
			object.transactionLock(uid);

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

			object.transactionLock(uid);

			mv++;
			mwv++;

			return true;

		} else /* if (mrv > 0) */{
			// The transaction already read from the object, so we have access
			// to it.
			// There's no need to buffer writes for now, so proceed as normal.

			object.transactionLock(uid);

			if (snapshot.getReadVersion() != object.getCurrentVersion()) {
				object.transactionUnlockForce(uid);
				transaction.rollback();
				throw new RollbackForcedException("Rollback forced during invocation.");
			}

			mv++;
			mwv++;

			return false;
		}
	}

	public void postWrite() throws RemoteException {

		if (mwv == wub || mv == ub) {
			// If mrv > 0 then there's no need for a new thread, because we
			// already have access.
			if (mrv > 0) {
				// Transaction already has access to, because there were reads.

				// Create buffer for accessing objects after release.
				try {
					buffer = object.clone();
				} catch (CloneNotSupportedException e) {
					e.printStackTrace();
					object.transactionUnlock(uid);
					throw new RemoteException(e.getLocalizedMessage(), e.getCause());
				}

				// Release.
				object.setCurrentVersion(px); // 27
				releaseTransaction(); // 29

			} else {
				writeThreadExists = true;
				SynchThread.get().addWriter(this);
			}
		}

		// in all cases
		object.transactionUnlock(uid);
	}

	public boolean preRead() throws RemoteException {

		if (mode == Mode.READ_ONLY) {
			// Read-only optimization (green).
			// We don't check for UB etc. because we already released this
			// object anyway, and we're using a buffer.
			try {
				// Synchronize with the Read Thread
				readSemaphore.acquire(1);
				readSemaphore.release(1);
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

			return true;
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
				object.transactionLock(uid);
				snapshot = object.snapshot();
			} else {
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

			return false;

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

				object.transactionLock(uid);

				snapshot = object.snapshot();

				try {
					writeRecorder.applyChanges(object);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RemoteException(e.getLocalizedMessage(), e);
				}

				// Prevent recorder from being used again
				writeRecorder = null;

				// Potentially create buffer for accessing objects after release
				// or remove buffer. Maybe?

			} else {
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

			return false;
		}

		// If the writes already reached their upper bound, operate on buffers.
		{
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

			return true;
		}
	}

	public boolean preAny() throws RemoteException {
		throw new RemoteException("Operation not supported.");
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
			// Empty.
		} else {
			if (mv == ub) {
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
	}

	public void finishTransaction(boolean restore, boolean readThread) throws RemoteException {
		if (!readThread && mode == Mode.READ_ONLY) { // 65
			// empty
		} else {
			TransactionFailureMonitor.getInstance().stopMonitoring(this);

			object.finishTransaction(uid, snapshot, restore);

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
					//FIXME WTF? something should go here, surely
				}
			}

			synchronized (this) {
				if (writeRecorder != null) {
					object.waitForCounter(px - 1); // 24
					object.transactionLock(uid);

					// We have to make a snapshot, else it thinks we didn't read
					// the object and in effect we don't get cv and rv.
					snapshot = object.snapshot(); // 28

					try {
						writeRecorder.applyChanges(object); // 24-25
					} catch (Exception e) {
						e.printStackTrace();
						throw new RemoteException(e.getLocalizedMessage(), e.getCause());
					}

					// Prevent recorder from being used again.
					writeRecorder = null;

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
			object.transactionLock(uid);
			snapshot = object.snapshot();
		} else {
			object.transactionLock(uid);
		}

		object.setCurrentVersion(px);
		releaseTransaction();

		object.transactionUnlock(uid);

		over = true;
		// snapshot = null;
	}

	public void lock() throws RemoteException {
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
			SynchThread.get().addReader(this);
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
}
