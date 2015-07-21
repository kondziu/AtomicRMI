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
import java.util.LinkedList;
import java.util.UUID;

import put.atomicrmi.optsva.TransactionRef;
import put.atomicrmi.optsva.Access.Mode;
import put.atomicrmi.optsva.sync.TaskController;
import put.atomicrmi.optsva.sync.TransactionFailureMonitorImpl;

/**
 * A write-only object proxy. It uses a log buffer throughout and applies
 * changes only on commit.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 */
// TODO only implement IObjectProxy
public class UpdateObjectProxyImpl extends ObjectProxyImpl {

	private static final long serialVersionUID = 4042112660455555346L;

	public UpdateObjectProxyImpl(TransactionRef transaction, UUID tid, TransactionalUnicastRemoteObject object, long writes)
			throws RemoteException {
		super(transaction, tid, object, writes, 0, writes, Mode.WRITE_ONLY);
	}

	@Override
	public BufferType preRead() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void postRead() {
		throw new UnsupportedOperationException();
	}

	@Override
	public BufferType preWrite() throws RemoteException {
		object.transactionLock(uid);
		if (mwv == 0 && mv == 0)
			logBuffer = new LinkedList<Invocation>();

		mv++;
		mwv++;

		return BufferType.LOG_BUFFER;
	}

	@Override
	public void postWrite() throws RemoteException {
		object.transactionUnlock(uid);
	}

	@Override
	public void finishTransaction(boolean restore, boolean readThread) throws RemoteException {
		TransactionFailureMonitorImpl.getInstance().stopMonitoring(this);

		object.finishTransaction(uid, snapshot, restore);
		TaskController.theOneThread.ping();

		over = true;
		snapshot = null;
	}

	@Override
	public void update() throws RemoteException {
		try {
			object.waitForCounter(px - 1);

			object.transactionLock(uid);

			/** Short circuit, if no writes. */
			if (logBuffer == null) {
				object.transactionUnlock(uid);
				return;
			}

			/**
			 * We have to make a snapshot, else it thinks we didn't read the
			 * object and in effect we don't get cv and rv.
			 */
			// TODO fakeSnapshot?
			snapshot = object.snapshot();
			
			applyWriteLog();

			/** Prevent recorder from being used again */
			logBuffer = null;

			/**
			 * Create buffer for accessing objects after release or remove
			 * buffer.
			 */
			copyBuffer = null;

			/** Release object. */
			object.setCurrentVersion(px);
			releaseTransaction();

		} catch (Exception e) {
			// XXX the client-side should see the exceptions from this thread.
			e.printStackTrace();
			throw new RuntimeException(e);
		} finally {
			object.transactionUnlock(uid);
		}
	}
}
