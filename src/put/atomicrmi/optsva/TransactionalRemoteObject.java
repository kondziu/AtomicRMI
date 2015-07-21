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
package put.atomicrmi.optsva;

import java.rmi.RemoteException;
import java.util.UUID;

import put.atomicrmi.optsva.Access.Mode;
import put.atomicrmi.optsva.objects.ObjectProxy;
import put.atomicrmi.optsva.objects.TransactionalUnicastRemoteObject;
import put.atomicrmi.optsva.sync.TransactionFailureMonitor;
import put.util.ids.IdentifiableRemote;

/**
 * Internal transactional mechanism of {@link TransactionalUnicastRemoteObject}
 * class. Specifies methods required to initiate remote transaction.
 * 
 * @author Wojciech Mruczkiewicz
 */
public interface TransactionalRemoteObject extends IdentifiableRemote {

	/**
	 * Creates and gives a remote object proxy. Object proxy wraps an instance
	 * of particular remote object and provides mechanism to monitor
	 * invocations.
	 * 
	 * @param transaction
	 *            a transaction remote object.
	 * @param tid
	 *            transaction unique identifier.
	 * @param calls
	 *            upper bound on number of remote object invocations.
	 * @param reads
	 *            upper bound on the number of reads.
	 * @param writes
	 *            upper bound on number of writes.
	 * @param mode
	 *            access mode (read-only, write-only, etc.)
	 * @return an instance of object proxy that wraps this remote object.
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	ObjectProxy createProxy(TransactionRef transaction, UUID tid, long calls, long reads, long writes, Mode mode)
			throws RemoteException;

	/**
	 * Creates and gives a remote object proxy to a write-only object in a
	 * write-only transaction.
	 * 
	 * @param transaction
	 *            a transaction remote object.
	 * @param tid
	 *            transaction unique identifier.
	 * @param writes
	 *            upper bound on number of writes.
	 * @return an instance of object proxy that wraps this remote object.
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	ObjectProxy createUpdateProxy(TransactionRef transaction, UUID tid, long writes) throws RemoteException;

	/**
	 * Gives a transaction failure monitor used at specific node where this
	 * remote object is placed.
	 * 
	 * @return a remote handle to transaction failure detector.
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	TransactionFailureMonitor getFailureMonitor() throws RemoteException;
	

}
