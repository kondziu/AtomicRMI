package put.sync;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.BrokenBarrierException;

/**
 * @author Konrad Siek
 */
public interface Barrier extends Remote {
	/**
	 * Waits until all {@linkplain #getParties parties} have invoked
	 * <tt>await</tt> on this barrier.
	 * 
	 * <p>
	 * If the current thread is not the last to arrive then it is disabled for
	 * thread scheduling purposes and lies dormant until one of the following
	 * things happens:
	 * <ul>
	 * <li>The last thread arrives; or
	 * <li>Some other thread {@linkplain Thread#interrupt interrupts} the
	 * current thread; or
	 * <li>Some other thread {@linkplain Thread#interrupt interrupts} one of the
	 * other waiting threads; or
	 * <li>Some other thread times out while waiting for barrier; or
	 * <li>Some other thread invokes {@link #reset} on this barrier.
	 * </ul>
	 * 
	 * <p>
	 * If the current thread:
	 * <ul>
	 * <li>has its interrupted status set on entry to this method; or
	 * <li>is {@linkplain Thread#interrupt interrupted} while waiting
	 * </ul>
	 * then {@link InterruptedException} is thrown and the current thread's
	 * interrupted status is cleared.
	 * 
	 * <p>
	 * If the barrier is {@link #reset} while any thread is waiting, or if the
	 * barrier {@linkplain #isBroken is broken} when <tt>await</tt> is invoked,
	 * or while any thread is waiting, then {@link BrokenBarrierException} is
	 * thrown.
	 * 
	 * <p>
	 * If any thread is {@linkplain Thread#interrupt interrupted} while waiting,
	 * then all other waiting threads will throw {@link BrokenBarrierException}
	 * and the barrier is placed in the broken state.
	 * 
	 * <p>
	 * If the current thread is the last thread to arrive, and a non-null
	 * barrier action was supplied in the constructor, then the current thread
	 * runs the action before allowing the other threads to continue. If an
	 * exception occurs during the barrier action then that exception will be
	 * propagated in the current thread and the barrier is placed in the broken
	 * state.
	 * 
	 * @return the arrival index of the current thread, where index <tt>
	 *         {@link #getParties()} - 1</tt> indicates the first to arrive and
	 *         zero indicates the last to arrive
	 * @throws InterruptedException
	 *             if the current thread was interrupted while waiting
	 * @throws BrokenBarrierException
	 *             if <em>another</em> thread was interrupted or timed out while
	 *             the current thread was waiting, or the barrier was reset, or
	 *             the barrier was broken when {@code await} was called, or the
	 *             barrier action (if present) failed due an exception.
	 */
	public int enter() throws InterruptedException, BrokenBarrierException,
			RemoteException;
}
