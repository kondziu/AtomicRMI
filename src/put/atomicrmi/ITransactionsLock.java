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

/**
 * Specification of a distributed JavaRMI lock. Provides functionality to
 * synchronize remote nodes. This is an internal AtomicRMI mechanism.
 * 
 * @author Wojciech Mruczkiewicz
 */
public interface ITransactionsLock extends Remote {
	/**
	 * Locks this lock for exclusive access. This method can block and wait
	 * until other node releases previously obtained lock.
	 * 
	 * @throws RemoteException
	 *             when remote execution failed or lock could not be obtained.
	 */
	void lock() throws RemoteException;

	/**
	 * Releases previously obtained lock.
	 * 
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	void unlock() throws RemoteException;
}
