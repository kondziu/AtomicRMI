/*
 * Atomic RMI
 *
 * Copyright 2009-2010 Piotr Kryger <Piotr.Kryger@gmail.com>
 * 					   Wojciech Mruczkiewicz <Wojciech.Mruczkiewicz@cs.put.poznan.pl>
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

/**
 * Simple semaphore supporting long number of permits. Used instead of
 * {@link java.util.concurrent.Semaphore} class.
 * {@link java.util.concurrent.Semaphore} is not suitable for implementing
 * versioning algorithm because it makes possible the following scenario (even
 * with fairness set to <code>false</code>, JDK 1.6.0_03):
 * <ol>
 * <li>semaphore has 0 permits available
 * <li>thread T1 tries to acquire 2 permits and waits
 * <li>thread T2 tries to acquire 1 permit and waits
 * <li>thread T3 releases 1 permit
 * <li>thread T2 still waits, although i could proceed (there is now enough
 * permits for it)
 * </ol>
 * 
 * 
 * @author Piotr Kryger
 * @author Wojciech Mruczkiewicz
 */
public class Semaphore {
	/**
	 * Number of permissions currently available.
	 */
	private long available;

	/**
	 * Initializes new semaphore.
	 * 
	 * @param initial
	 *            initial number of permits
	 */
	public Semaphore(long initial) {
		available = initial;
	}

	/**
	 * Releases specified number of permits and wakes up all threads waiting for
	 * permits.
	 * 
	 * @param perm
	 *            number of permits to release
	 */
	public synchronized void release(long perm) {	
		available += perm;
		notifyAll();
	}

	/**
	 * Tries to acquire specified number of permits. If there is enough permits
	 * available, the number of permits is decreased and thread execution
	 * continues. Otherwise thread is suspended until call to
	 * {@link #release(long)} makes proper number of permits available.
	 * 
	 * @param perm
	 *            number of permits to acquire
	 * @throws InterruptedException
	 */
	public synchronized void acquire(long perm) throws InterruptedException {	
		while (perm > available)
			wait();

		available -= perm;
	}

	/**
	 * Gives the current value of a semaphore.
	 * 
	 * @return semaphore value.
	 */
	public synchronized long getAvailable() {
		return available;
	}
	
	/**
	 * Tries to acquire specified number of permits. If there is enough permits
	 * available, the number of permits is decreased and thread execution
	 * continues. Otherwise the thread does not wait, but returns <code>false</code>.
	 * 
	 * @param perm
	 *            number of permits to acquire
	 * @throws InterruptedException
	 */
	public synchronized boolean tryAcquire(long perm) {
		if (perm > available)
			return false;

		available -= perm;
		return true;
	}
}
