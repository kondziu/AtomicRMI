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

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

/**
 * An interface for remote object proxy. This allows to access remote object
 * proxy remotely and control the wrapped remote object invocation.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 */
public interface IObjectProxy extends Remote {

	/**
	 * Gives the remote reference to the remote object that is being wrapped.
	 * 
	 * @return reference to wrapped object.
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	Object getWrapped() throws RemoteException;

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
	 * Action performed before every remote method invocation. It should update
	 * versioning and checkpoint counters appropriately and obtain required
	 * locks.
	 * 
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	void preSync() throws RemoteException;

	/**
	 * Action performed after every remote method invocation. It should update
	 * versioning and checkpoint counters appropriately and release appropriate
	 * locks.
	 * 
	 * @throws RemoteException
	 *             when remote execution fails.
	 */
	void postSync() throws RemoteException;

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
	void finishTransaction(boolean restore) throws RemoteException;

	/**
	 * Waits for checkpoint counter to reach this object's private counter
	 * value.
	 * 
	 * @return <code>true</code> when changes to this object can be committed,
	 *         <code>false</code> when changes must be restored.
	 * @throws RemoteException
	 *             when remote invocation fails.
	 */
	boolean waitForSnapshot() throws RemoteException;

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
	 * TODO
	 * 
	 * @throws RemoteException
	 */
	void lock() throws RemoteException;
	
	/**
	 * TODO
	 * 
	 * @throws RemoteException
	 */
	void unlock() throws RemoteException;
	
	/**
	 * TODO
	 * 
	 * @throws RemoteException
	 */
	UUID getSortingKey() throws RemoteException;
}