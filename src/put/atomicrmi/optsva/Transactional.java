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

/**
 * Interface specifies transaction method required for retry operation support.
 * If retry operation is necessary then class implementing this interfaces
 * contains main transaction method. Transaction is started using
 * {@link Transaction#start(DTransactable)} method and must be commited or
 * rolled-back at the end.
 * 
 * @author Wojciech Mruczkiewicz
 */
public interface Transactional {

	/**
	 * Implementation of main transaction thread. This method is implemented by
	 * client of AtomicRMI library and allows to use commit, rollback and retry
	 * operations during the execution.
	 * 
	 * @param transaction
	 *            transaction instance.
	 * @throws RemoteException
	 *             when remote exception occur during transaction execution.
	 */
	public void atomic(Transaction transaction) throws RemoteException;
}
