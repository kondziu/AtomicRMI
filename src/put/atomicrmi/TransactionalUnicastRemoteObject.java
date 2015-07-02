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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

import put.atomicrmi.Access.Mode;

/**
 * Base class for all remote object implementations that are part of some
 * transactional executions. This class supply remote object with versioning
 * counters and checkpoint capabilities. None of the method in this class needs
 * to be accessed by client of AtomicRMI library. Transactional remote objects
 * must only extend this class instead of {@link UnicastRemoteObject}.
 * 
 * @author Wojciech Mruczkiewicz
 */
public class TransactionalUnicastRemoteObject extends UnicastRemoteObject implements ITransactionalRemoteObject,
		Stateful {

	/**
	 * Stores snapshot of particular remote object together with snapshot
	 * version information.
	 * 
	 * @author Wojciech Mruczkiewicz
	 */
	class Snapshot {

		/**
		 * The binary representation of remote object.
		 */
		private byte[] image;

		/**
		 * Version information that determines when the snapshot was taken.
		 */
		private long rv;

		/**
		 * Constructs the snapshot with given version and object's image.
		 * 
		 * @param image
		 *            a serialized remote object.
		 * @param readVersion
		 *            version when the serialization occurred.
		 */
		Snapshot(byte[] image, long readVersion) {
			this.image = image;
			rv = readVersion;
		}

		/**
		 * Gives the serialized image reference.
		 * 
		 * @return the serialized image.
		 */
		byte[] getImage() {
			return image;
		}

		/**
		 * Gives the version of an image when the serialization occurred.
		 * 
		 * @return image version.
		 */
		long getReadVersion() {
			return rv;
		}
	}

	/**
	 * Randomly generated serialization UID.
	 */
	private static final long serialVersionUID = 1387578756217285118L;

	/**
	 * Global versioning counter of this remote object.
	 */
	private transient long gx = 0;

	/**
	 * The versioning counter for this remote object. The value of this
	 * semaphore determines the actual versioning counter value.
	 */
	private transient Semaphore lv = new Semaphore(0);

	/**
	 * The checkpoint counter for this remote object. The value of this
	 * semaphore determines the actual checkpoint counter value.
	 */
	private transient Semaphore lt = new Semaphore(0);

	/**
	 * Current version of this remote object. This value can be decreased during
	 * snapshot restoration.
	 */
	private transient LongHolder cv = new LongHolder(0);

	/**
	 * Lock used to implement reentrant per-transaction lock.
	 */
	private transient LongHolder lock = new LongHolder(0);

	/**
	 * Unique identifier of a transaction that locked this object proxy.
	 */
	private transient Object lockedId;

	/**
	 * Unique identifier of this object.
	 */
	final private UUID uid;

	protected TransactionalUnicastRemoteObject() throws RemoteException {
		uid = UUID.randomUUID();
	}

	protected TransactionalUnicastRemoteObject(UUID uniqueID) throws RemoteException {
		this.uid = uniqueID;
	}

	protected TransactionalUnicastRemoteObject(int port) throws RemoteException {
		super(port);
		uid = UUID.randomUUID();
	}

	protected TransactionalUnicastRemoteObject(UUID uniqueID, int port) throws RemoteException {
		super(port);
		this.uid = uniqueID;
	}

	protected TransactionalUnicastRemoteObject(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf)
			throws RemoteException {
		super(port, csf, ssf);
		uid = UUID.randomUUID();
	}

	protected TransactionalUnicastRemoteObject(UUID uniqueID, int port, RMIClientSocketFactory csf,
			RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.uid = uniqueID;
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	public IObjectProxy createProxy(ITransaction transaction, UUID tid, long calls, long reads, long writes, Mode mode)
			throws RemoteException {
		return (IObjectProxy) ObjectProxyHandler.create(new ObjectProxy(transaction, tid, this, calls, reads, writes,
				mode));
	}

	public IObjectProxy createUpdateProxy(ITransaction transaction, UUID tid, long writes) throws RemoteException {
		return (IObjectProxy) ObjectProxyHandler.create(new UpdateObjectProxy(transaction, tid, this, writes));
	}

	public ITransactionFailureMonitor getFailureMonitor() throws RemoteException {
		return TransactionFailureMonitor.getInstance();
	}

	/**
	 * Gives the current version of this remote object.
	 * 
	 * @return current version of remote object.
	 */
	long getCurrentVersion() {
		synchronized (cv) {
			return cv.value;
		}
	}

	/**
	 * Sets the current version of this remote object.
	 * 
	 * @param value
	 *            the remote object current value.
	 */
	void setCurrentVersion(long value) {
		synchronized (cv) {
			cv.value = value;
		}
	}

	/**
	 * Locks this remote object for given transaction usage only.
	 * 
	 * @param tid
	 *            transaction identifier.
	 * @throws TransactionException
	 *             when error occurred during waiting for lock to be released.
	 */
	void transactionLock(Object tid) throws TransactionException {
		synchronized (lock) {
			try {
				while (lockedId != null && !lockedId.equals(tid))
					lock.wait();
			} catch (InterruptedException e) {
				throw new TransactionException("Interrupted while locking version counter.", e);
			}

			lockedId = tid;
			lock.value++;
		}
	}

	/**
	 * Releases a single previously established lock. The object can be accessed
	 * by other transactions only when all the locks are released.
	 * 
	 * @param tid
	 *            transaction identifier.
	 */
	void transactionUnlock(UUID tid) {
		synchronized (lock) {
			if (lockedId.equals(tid)) {
				lock.value--;

				if (lock.value == 0) {
					lockedId = null;
					lock.notify();
				}
			}
		}
	}

	/**
	 * Releases all the acquired locks. This object can be accessed by other
	 * transactions after this operation.
	 * 
	 * @param tid
	 *            transaction identifier.
	 * @throws TransactionException
	 *             when lock was acquired by other transaction.
	 */
	void transactionUnlockForce(Object tid) throws TransactionException {
		synchronized (lock) {
			if (lockedId.equals(tid)) {
				lock.value = 0;
				lockedId = null;
				lock.notify();
			} else
				throw new TransactionException("Invalid state when releasing transactional remote object lock.");
		}
	}

	/**
	 * Makes the snapshot of the current state of this remote object.
	 * 
	 * @return the created snapshot.
	 * @throws TransactionException
	 *             when error occurs during snapshot creation.
	 */
	Snapshot snapshot() throws TransactionException {
		return new Snapshot(serializeThis(), cv.value);
	}

	/**
	 * This method increments the global versionig counter and is called during
	 * transaction start.
	 * 
	 * @param tid
	 *            transaction identifier.
	 * @return the new value of global versioning counter.
	 */
	long startTransaction(Object tid) {
		return ++gx;
	}

	/**
	 * Increments the versioning counter for this remote object. This allows for
	 * other transactions to start the execution.
	 */
	void releaseTransaction() {
		lv.release(1);
	}

	/**
	 * Blocks until versioning counter value reaches the required value.
	 * 
	 * @param value
	 *            the required versioning counter value.
	 * @throws TransactionException
	 *             when error occurred when waiting for the versioning counter.
	 */
	void waitForCounter(long value) throws TransactionException {
		try {
			lv.acquire(value);
			lv.release(value);
		} catch (InterruptedException e) {
			throw new TransactionException("Error waiting for object version", e);
		}
	}

	/**
	 * Checks if the counter value reaches the required value. Does not block,
	 * but returns <code>false</code> if not.
	 * 
	 * @param value
	 *            the required versioning counter value.
	 * @returns <code>true</code> if acquired, <code>false</code> otherwise.
	 */
	boolean tryWaitForCounter(long value) throws TransactionException {
		// System.out.println("acquire " + lv.getAvailable());
		boolean acquired = lv.tryAcquire(value);
		// System.out.println("acquire " + acquired);
		if (!acquired)
			return false;
		else
			lv.release(value);
		return true;
	}

	/**
	 * Blocks until checkpoint counter reaches the required value.
	 * 
	 * @param value
	 *            the required value of checkpoint counter.
	 * @throws TransactionException
	 *             when error occured when waiting for the checkpoint counter.
	 */
	void waitForSnapshot(long value) throws TransactionException {
		try {
			lt.acquire(value);
			lt.release(value);
		} catch (InterruptedException e) {
			throw new TransactionException("Error waiting for object version", e);
		}
	}

	boolean tryWaitForSnapshot(long value) throws TransactionException {
		boolean acquired = lt.tryAcquire(value);
		if (!acquired)
			return false;
		else
			lt.release(value);
		return true;
	}

	/**
	 * Terminates the specific transaction. It releases the checkpoint counter
	 * and performs snapshot rollback if necessary.
	 * 
	 * @param tid
	 *            transaction identifier.
	 * @param snapshot
	 *            the snapshot taken.
	 * @param restore
	 *            if <code>true</code> then this object should be restored to
	 *            snapshot given in arguments.
	 * @throws TransactionException
	 *             when error occurs during snapshot restoration.
	 */
	void finishTransaction(Object tid, Snapshot snapshot, boolean restore) throws TransactionException {
		if (snapshot == null) {
			lt.release(1);
			return;
		}

		if (restore && snapshot.getReadVersion() < getCurrentVersion()) {

			// Lock before restoring.
			transactionLock(tid);

			restoreThis(snapshot.getImage());
			setCurrentVersion(snapshot.getReadVersion());

			// Forced unlock is necessary because of possible failures.
			transactionUnlockForce(tid);
		}

		lt.release(1);
	}

	/**
	 * Performs the serialization of this object to array of bytes.
	 * 
	 * @return binary representation of this object.
	 * @throws TransactionException
	 *             when error occurs during serialization.
	 */
	private byte[] serializeThis() throws TransactionException {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bos);

			try {
				out.writeObject(this);
				return bos.toByteArray();
			} catch (IOException e) {
				throw new TransactionException("Unable to make snapshot.", e);
			} finally {
				out.close();
			}
		} catch (IOException e) {
			throw new TransactionException("Unable to make snapshot.", e);
		}
	}

	/**
	 * Restores the image of stored snapshot to this object.
	 * 
	 * @param image
	 *            the stored image of this object.
	 * @throws TransactionException
	 *             when error occurs during object restoration.
	 */
	private void restoreThis(byte[] image) throws TransactionException {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(image);
			ObjectInputStream in = new ObjectInputStream(bis);

			try {
				Object obj = in.readObject();

				Field[] fields = obj.getClass().getDeclaredFields();
				Field.setAccessible(fields, true);

				try {
					for (Field f : fields) {
						if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
							Object val = f.get(obj);
							f.set(this, val);
						}
					}
				} catch (IllegalArgumentException e) {
					throw new TransactionException("Unable to restore snapshot.", e);
				} catch (IllegalAccessException e) {
					throw new TransactionException("Unable to restore snapshot.", e);
				}
			} catch (IOException e) {
				throw new TransactionException("Unable to restore snapshot.", e);
			} catch (ClassNotFoundException e) {
				throw new TransactionException("Unable to restore snapshot.", e);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			throw new TransactionException("Unable to restore snapshot.", e);
		}
	}

	public Object getSortingKey() throws RemoteException {
		return uid;
	}

	public void set(String fieldName, FieldType type, Object value) throws RemoteException {
		try {
			Field field = this.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			switch (type) {
			case Boolean:
				field.setBoolean(this, ((Boolean) value).booleanValue());
				break;
			case Byte:
				field.setByte(this, ((Byte) value).byteValue());
				break;
			case Char:
				field.setChar(this, ((Character) value).charValue());
				break;
			case Double:
				field.setDouble(this, ((Double) value).doubleValue());
				break;
			case Float:
				field.setFloat(this, ((Float) value).floatValue());
				break;
			case Int:
				field.setInt(this, ((Integer) value).intValue());
				break;
			case Long:
				field.setLong(this, ((Long) value).longValue());
				break;
			case Object:
				field.set(this, value);
				break;
			case Short:
				field.setShort(this, ((Short) value).shortValue());
				break;
			}
		} catch (Exception e) {
			throw new RemoteException(e.getLocalizedMessage(), e.getCause());
		}
	}

	@Override
	public UUID getUID() throws RemoteException {
		return uid;
	}
}
