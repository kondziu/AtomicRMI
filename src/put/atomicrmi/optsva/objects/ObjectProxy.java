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

import java.rmi.RemoteException;

import put.atomicrmi.optsva.Access.Mode;
import put.util.ids.IdentifiableRemote;

/**
 * An interface for remote object proxy. This allows to access remote object
 * proxy remotely and control the wrapped remote object invocation.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 */
public interface ObjectProxy extends IdentifiableRemote {

	enum BufferType {
		LOG_BUFFER, COPY_BUFFER, NONE
	}

	/**
	 * Gives the remote reference to the remote object that is being wrapped.
	 * 
	 * @param bufferred
	 *            return the actual reference or the buffer.
	 * @return reference to wrapped object.
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	Object getWrapped() throws RemoteException;

	/**
	 * Returns the copy buffer of the remote object that is being wrapped.
	 * 
	 * @return copy of the wrapped object.
	 * @throws RemoteException
	 */
	Object getBuffer() throws RemoteException;

	/**
	 * Notifies this object proxy that transaction is starting. The failure
	 * monitoring should be started and internal counter initialized to initial
	 * values.
	 * 
	 * @throws RemoteException
	 *             when remote invocation fails.
	 */
	void startTransaction() throws RemoteException;

	/**
	 * Notifies this remote object to release version counters and allow other
	 * transactions to execute.
	 * 
	 * @throws RemoteException
	 *             when remote invocation fails.
	 */
	void releaseTransaction() throws RemoteException;

	/**
	 * Terminates the transaction. This method should release the checkpoint,
	 * stop failure monitoring and drop the references to remote objects. It
	 * also must release the checkpoint counter and allow for other transactions
	 * to commit.
	 * 
	 * This method can also perform rollback action if <code>restore</code>
	 * parameter is true.
	 * 
	 * @param restore
	 *            determines if rollback action should be performed. If
	 *            <code>true</code> then changes to this remote object should be
	 *            rolled-back.
	 * @throws RemoteException
	 *             when remote invocation fails.
	 */
	void finishTransaction(boolean restore, boolean readThread) throws RemoteException;

	/**
	 * Waits for checkpoint counter to reach this object's private counter
	 * value.
	 * 
	 * @return <code>true</code> when changes to this object can be committed,
	 *         <code>false</code> when changes must be restored.
	 * @throws RemoteException
	 *             when remote invocation fails.
	 */
	boolean waitForSnapshot(boolean readThread) throws RemoteException;

	/**
	 * Declare that the object will no longer be used by the current transaction
	 * and allow it to be used by other transactions.
	 * 
	 * This method is only a proposal and requires extensive testing.
	 * 
	 * @author K. Siek
	 * @throws RemoteException
	 */
	void free() throws RemoteException;

	/**
	 * Lock this object.
	 * 
	 * @throws RemoteException
	 */
	void lock() throws RemoteException;

	/**
	 * Unlock this object.
	 * 
	 * @throws RemoteException
	 */
	void unlock() throws RemoteException;

	/**
	 * Return specified access mode for this proxy object.
	 * 
	 * @return access mode
	 * @throws RemoteException
	 */
	Mode getMode() throws RemoteException;

	/**
	 * Force object to update its state from its own buffers.
	 * 
	 * @throws RemoteException
	 */
	void update() throws RemoteException;

	/**
	 * Admit a method execution into the log buffer.
	 * 
	 * @param methodName
	 *            method name
	 * @param argTypes
	 *            argument types
	 * @param args
	 *            argument values
	 * @throws RemoteException
	 */
	void log(String methodName, Class<?>[] argTypes, Object[] args) throws RemoteException;

	/**
	 * Execute pre-read synchronization and preparation.
	 * 
	 * Action performed before every read remote method invocation. It should
	 * update versioning and checkpoint counters appropriately and obtain
	 * required locks.
	 * 
	 * @return a constant indicating which object to use for performing actual
	 *         operations: a log buffer, a copy buffer, or the actual wrapped
	 *         object.
	 * @throws RemoteException
	 */
	BufferType preRead() throws RemoteException;

	/**
	 * Perform pre-write synchronization and preparation.
	 * 
	 * Action performed before every write remote method invocation. It should
	 * update versioning and checkpoint counters appropriately and obtain
	 * required locks.
	 * 
	 * @return a constant indicating which object to use for performing actual
	 *         operations: a log buffer, a copy buffer, or the actual wrapped
	 *         object.
	 * @throws RemoteException
	 */
	BufferType preWrite() throws RemoteException;

	/**
	 * Perform post-read synchronization and potential releasing.
	 * 
	 * Action performed after every read remote method invocation. It should
	 * update versioning and checkpoint counters appropriately and release
	 * appropriate locks.
	 * 
	 * @throws RemoteException
	 */
	void postRead() throws RemoteException;

	/**
	 * Perform post-write synchronization and potential releasing.
	 * 
	 * Action performed after every write remote method invocation. It should
	 * update versioning and checkpoint counters appropriately and release
	 * appropriate locks.
	 * 
	 * @throws RemoteException
	 */
	void postWrite() throws RemoteException;
}
