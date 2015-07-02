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
import java.util.UUID;

import put.atomicrmi.Access.Mode;

/**
 * An implementation of {@link IObjectProxy} interface. It is required to
 * control remote method invocations and implement versioning algorithm.
 * 
 * @author Wojciech Mruczkiewicz, Konrad Siek
 */
class UpdateObjectProxy extends ObjectProxy {

	private static final long serialVersionUID = 4042112660455555346L;

	public UpdateObjectProxy(ITransaction transaction, UUID tid, TransactionalUnicastRemoteObject object, long writes)
			throws RemoteException {
		super(transaction, tid, object, writes, 0, writes, Mode.WRITE_ONLY);
	}

	@Override
	public boolean preSync(Mode accessType) throws RemoteException {

		object.transactionLock(uid);
		if (mwv == 0 && mv == 0) {
			writeRecorder = new StateRecorder();
			try {
				buffer = Instrumentation.transform(object.getClass(), object, writeRecorder);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RemoteException(e.getLocalizedMessage(), e.getCause());
			}
		}

		mv++;
		mwv++;

		return true;
	}

	@Override
	public void postSync(Mode accessType) throws RemoteException {
		object.transactionUnlock(uid);
	}

	// @Override
	// public void startTransaction() throws RemoteException {
	// //TransactionFailureMonitor.getInstance().startMonitoring(this);
	// //
	//
	// mv = 0;
	// mwv = 0;
	//
	// over = false;
	// }

	@Override
	public void finishTransaction(boolean restore, boolean readThread) throws RemoteException {
		TransactionFailureMonitor.getInstance().stopMonitoring(this);

		object.finishTransaction(uid, snapshot, restore);
		OneThreadToRuleThemAll.theOneThread.ping("lt");

		over = true;
		snapshot = null;		
	}

	@Override
	public void update() throws RemoteException {
		try {
			object.waitForCounter(px - 1);

			object.transactionLock(uid);

			// Short circuit, if no writes.
			if (writeRecorder == null) {
				object.transactionUnlock(uid);
				return;
			}

			// We have to make a snapshot, else it thinks we didn't read
			// the object and in effect we don't get cv and rv.
			snapshot = object.snapshot();

			writeRecorder.applyChanges(object);

			// Prevent recorder from being used again
			writeRecorder = null;

			// Create buffer for accessing objects after release or
			// remove buffer.
			buffer = null;

			// Release object.
			object.setCurrentVersion(px);
			releaseTransaction(); // 29

		} catch (Exception e) {
			// FIXME the client-side should see the exceptions from this thread.
			e.printStackTrace();
			throw new RuntimeException(e);
		} finally {
			object.transactionUnlock(uid);
		}

	}
}
