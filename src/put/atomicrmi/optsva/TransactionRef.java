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

import put.util.ids.IdentifiableRemote;

/**
 * Remote interface for transaction execution control. This is a public
 * interface that provides methods to terminate transaction remotely in various
 * ways.
 * 
 * This interface does not provides remote startup of a transaction. Transaction
 * can only be started on node where they were created on.
 * 
 * @author Wojciech Mruczkiewicz
 */
public interface TransactionRef extends IdentifiableRemote {

	/**
	 * Terminates remote transaction and commits all the changes made. It is
	 * however possible that transaction was forcibly rolled back during
	 * execution of this method.
	 * 
	 * @throws RemoteException
	 *             when remote execution failed.
	 * @throws RollbackForcedException
	 *             when changes were forcibly restored.
	 */
	void commit() throws RemoteException;

	/**
	 * Terminates remote transaction and restores all the changes made.
	 * 
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	void rollback() throws RemoteException;
}
