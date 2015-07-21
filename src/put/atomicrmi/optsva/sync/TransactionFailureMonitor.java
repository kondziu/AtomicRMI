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
package put.atomicrmi.optsva.sync;

import java.rmi.RemoteException;

import put.atomicrmi.optsva.Transaction;
import put.util.ids.IdentifiableRemote;

/**
 * Internal interface for transaction failure detector mechanism. Provides
 * methods for {@link Transaction} that allows to signal transaction liveness.
 * 
 * @author Wojciech Mruczkiewicz
 */
public interface TransactionFailureMonitor extends IdentifiableRemote {
	
	/**
	 * Sends a signal to transaction failure monitor with information that
	 * transaction is still alive.
	 * 
	 * @param id
	 *            identifier of transaction that signals liveness.
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	void heartbeat(Object id) throws RemoteException;
}
