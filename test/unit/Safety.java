package unit;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import soa.atomicrmi.RollbackForcedException;
import soa.atomicrmi.Transaction;
import soa.atomicrmi.TransactionException;
import soa.atomicrmi.TransactionFailureMonitor;
import unit.vars.Variable;
import unit.vars.VariableImpl;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Consistency tests for Atomic RMI.
 * 
 * The tests check for specific interleavings and check whether the behavior of
 * the Atomic RMI implementation adheres to the specification of SVA.
 * 
 * @author K. Siek
 */
public class Safety {

	private Registry registry;

	/**
	 * Helper function to get the state of a variable (non-transacitonally).
	 * 
	 * @param var
	 * @return value of var
	 */
	public int state(String var) throws RemoteException, NotBoundException {
		return ((Variable) registry.lookup(var)).read();
	}

	/**
	 * Create registry (port 1995) and transactional remote objects.
	 * 
	 * Creates objects x, y, z.
	 * 
	 * @throws Exception
	 */
	@Before
	public void populate() throws Exception {
		registry = LocateRegistry.createRegistry(1995);
		registry.bind("x", new VariableImpl("x", 0));
		registry.bind("y", new VariableImpl("y", 0));
		registry.bind("z", new VariableImpl("z", 0));
	}

	/**
	 * Remove transactional remote objects and registry.
	 * 
	 * @throws Exception
	 */
	@After
	public void depopulate() throws Exception {
		registry.unbind("x");
		registry.unbind("y");
		registry.unbind("z");
		UnicastRemoteObject.unexportObject(registry, true);
	}

	/**
	 * Forced abort test case.
	 * 
	 * <pre>
	 * T1 [   r(x)0 w(x)1              r(y)0 r(y)1 r(z)0 r(z)1              !
	 * T2   [             r(x)1 w(x)2              r(y)1 r(y)2 r(z)1 r(z)2    !
	 * </pre>
	 */
	class ForcedAbortOnComit extends MultithreadedTest {
		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				t.start();
				waitForTick(1);

				waitForTick(2);
				int vx = x.read();
				x.write(vx + 1);

				waitForTick(3);
				int vy = y.read();
				y.write(vy + 1);

				int vz = z.read();
				z.write(vz + 1);

				waitForTick(4);
				waitForTick(5);
				t.rollback();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread2() {
			Transaction t = null;
			try {
				int v;
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				waitForTick(1);
				t.start();

				waitForTick(2);
				v = x.read();
				x.write(v + 1);

				waitForTick(3);
				v = y.read();
				y.write(v + 1);

				v = z.read();
				z.write(v + 1);

				waitForTick(4);
				t.commit();
				waitForTick(5);

				Assert.fail("Transaction comitted when it should have aborted");

			} catch (RollbackForcedException e) {
			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}
	}

	/**
	 * Forced abort test case.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void forcedAbortOnCommit() throws Throwable {
		TestFramework.runOnce(new ForcedAbortOnComit());

		Assert.assertEquals(0, state("x"));
		Assert.assertEquals(0, state("y"));
		Assert.assertEquals(0, state("z"));
	}

	/**
	 * Forced abort and retry test case.
	 * 
	 * <pre>
	 * T1 [   r(x)0 w(x)1              r(y)0 r(y)1 r(z)0 r(z)1              !
	 * T2   [             r(x)1 w(x)2              r(y)1 r(y)2 r(z)1 r(z)2    ! ... [ r(x)0 w(x)1 ...
	 * </pre>
	 */
	class ForcedRetryOnComit extends MultithreadedTest {
		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				t.start();
				waitForTick(1);

				waitForTick(2);
				int vx = x.read();
				x.write(vx + 1);

				waitForTick(3);
				int vy = y.read();
				y.write(vy + 1);

				int vz = z.read();
				z.write(vz + 1);

				waitForTick(4);
				waitForTick(5);
				t.rollback();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread2() {
			Transaction t = null;
			boolean comitted = false;
			int i = 0;
			while (!comitted) {
				try {
					t = new Transaction();
					Variable x = t.accesses((Variable) registry.lookup("x"), 2);
					Variable y = t.accesses((Variable) registry.lookup("y"), 2);
					Variable z = t.accesses((Variable) registry.lookup("z"), 2);

					waitForTick(i + 1);
					t.start();

					waitForTick(i + 2);
					int vx = x.read();
					x.write(vx + 1);

					waitForTick(i + 3);
					int vy = y.read();
					y.write(vy + 1);

					int vz = z.read();
					z.write(vz + 1);

					waitForTick(i + 4);
					t.commit();
					waitForTick(i + 5);

					if (i == 0) {
						Assert.fail("Transaction comitted when it should have aborted");
					}

					comitted = true;

				} catch (RollbackForcedException e) {
				} catch (Exception e) {
					Assert.fail(e.getMessage());
				} finally {
					t.stopHeartbeat();
					i += 10;
				}
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}
	}

	/**
	 * Forced abort and retry test case.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void forcedRetryOnCommit() throws Throwable {
		TestFramework.runOnce(new ForcedRetryOnComit());

		Assert.assertEquals(1, state("x"));
		Assert.assertEquals(1, state("y"));
		Assert.assertEquals(1, state("z"));
	}

	/**
	 * Forced abort test case.
	 * 
	 * <pre>
	 * T1 [   r(x)0 w(x)1              !              
	 * T2   [             r(x)1 w(x)2    !r(y)1 !r(y)2 !r(z)1 !r(z)2
	 * </pre>
	 * 
	 * One of the operations in T2 should abort.
	 */
	class ForcedAbortOnInvoke extends MultithreadedTest {
		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				@SuppressWarnings("unused")
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				t.start();
				waitForTick(1);
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				waitForTick(3);
				int vy = y.read();
				y.write(vy + 1);

				t.rollback();
				waitForTick(4);

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread2() {
			Transaction t = null;
			try {
				int v;
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				waitForTick(1);
				t.start();
				waitForTick(2);

				v = x.read();
				x.write(v + 1);

				waitForTick(3);
				v = y.read();
				waitForTick(4);

				y.write(v + 1);
				v = z.read();
				z.write(v + 1);

				Assert.fail("Transaction attempted to commit, when it should have aborted on invoke.");
				t.commit();

			} catch (RollbackForcedException e) {
			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}
	}

	/**
	 * Forced abort test case.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void forcedAbortOnInvoke() throws Throwable {
		TestFramework.runOnce(new ForcedAbortOnInvoke());

		Assert.assertEquals(0, state("x"));
		Assert.assertEquals(0, state("y"));
		Assert.assertEquals(0, state("z"));
	}

	/**
	 * Forced abort test case.
	 * 
	 * <pre>
	 * T1 [   r(x)0 w(x)1              !              
	 * T2   [             r(x)1 w(x)2    !r(y)1 !r(y)2 !r(z)1 !r(z)2 ... [ r(x)0 w(x)1 ...
	 * </pre>
	 * 
	 * One of the operations in T2 should abort.
	 */
	class ForcedRetryOnInvoke extends MultithreadedTest {
		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				@SuppressWarnings("unused")
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				t.start();
				waitForTick(1);
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				waitForTick(3);
				int vy = y.read();
				y.write(vy + 1);

				t.rollback();
				waitForTick(4);

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread2() {
			Transaction t = null;
			boolean comitted = false;
			int i = 0;
			while (!comitted) {
				try {
					t = new Transaction();
					Variable x = t.accesses((Variable) registry.lookup("x"), 2);
					Variable y = t.accesses((Variable) registry.lookup("y"), 2);
					Variable z = t.accesses((Variable) registry.lookup("z"), 2);

					System.out.println(i + 1);

					waitForTick(i + 1);
					System.out.println("starting");
					t.start();
					System.out.println("started");
					waitForTick(i + 2);

					System.out.println(i + 2);

					int vx = x.read();
					x.write(vx + 1);

					System.out.println(i + 3);

					waitForTick(i + 3);
					int vy = y.read();
					waitForTick(i + 4);

					System.out.println(i + 4);

					y.write(vy + 1);
					int vz = z.read();
					z.write(vz + 1);

					if (i == 0) {
						Assert.fail("Transaction attempted to commit, when it should have aborted on invoke.");
					}

					t.commit();
					comitted = true;

				} catch (RollbackForcedException e) {
				} catch (Exception e) {
					Assert.fail(e.getMessage());
				} finally {
					t.stopHeartbeat();
					i += 10;
				}
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}
	}

	/**
	 * Forced abort test case.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void forcedRetryOnInvoke() throws Throwable {
		TestFramework.runOnce(new ForcedRetryOnInvoke());

		Assert.assertEquals(1, state("x"));
		Assert.assertEquals(1, state("y"));
		Assert.assertEquals(1, state("z"));
	}

	/**
	 * Atomicity and Isolation test case.
	 * 
	 * <pre>
	 * T1 [ r(x)0 w(x)1 r(y)0 w(y)1 ]
	 * T2  [                         r(x)1 w(x)2 r(y)1 w(y)2 ]
	 * </pre>
	 */
	class AtomicityAndIsolation extends MultithreadedTest {

		private void transaction() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));
				t.start();

				waitForTick(1);
				int v = x.read();
				x.write(v + 1);

				t.commit();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread1() {
			transaction();
		}

		public void thread2() {
			transaction();
		}
	}

	/**
	 * Atomicity and isolation test case.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void atomicityAndIsolation() throws Throwable {
		TestFramework.runOnce(new AtomicityAndIsolation());

		Assert.assertEquals(2, state("x"));
	}

	/**
	 * Atomicity and Isolation test case.
	 * 
	 * <pre>
	 * T1 [ r(x)0 w(x)1 r(y)0 w(y)1 ]
	 * T2  [            r(x)1 w(x)2 r(y)1 w(y)2 ]
	 * T3   [                        r(x)1 w(x)2 r(y)1 w(y)2 ]
	 * T4    [                                   r(x)1 w(x)2 r(y)1 w(y)2 ]
	 * T5     [                                              r(x)1 w(x)2 r(y)1 w(y)2 ]
	 * T6      [                                                         r(x)1 w(x)2 r(y)1 w(y)2 ]
	 * </pre>
	 */
	class AtomicityAndIsolationN extends MultithreadedTest {

		private void transaction(int i) {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));
				Variable y = t.accesses((Variable) registry.lookup("y"));

				waitForTick(i);
				t.start();

				int vx = x.read();
				x.write(vx + 1);

				Thread.sleep(i * 10);

				int vy = y.read();
				y.write(vy + 1);

				t.commit();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread1() {
			transaction(1);
		}

		public void thread2() {
			transaction(2);
		}

		public void thread3() {
			transaction(3);
		}

		public void thread4() {
			transaction(4);
		}

		public void thread5() {
			transaction(5);
		}

		public void thread6() {
			transaction(6);
		}
	}

	/**
	 * Atomicity and isolation test case.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void atomicityAndIsolationN() throws Throwable {
		TestFramework.runOnce(new AtomicityAndIsolationN());

		Assert.assertEquals(6, state("x"));
		Assert.assertEquals(6, state("y"));
	}

	/**
	 * Early release test case.
	 * 
	 * <pre>
	 * T1 [ r(x)0 w(x)1 r(y)0 w(y)1 ]
	 * T2  [            r(x)1 w(x)2 ]
	 * </pre>
	 * 
	 * Specifically checks whether T2 is free to operate on X while T1 operates
	 * on y.
	 */
	class EarlyRelease extends MultithreadedTest {

		AtomicInteger aint;

		@Override
		public void initialize() {
			aint = new AtomicInteger(0);
		}

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				int vy = y.read();
				y.write(vy + 1);

				waitForTick(3);

				// If early release worked correctly, T2 should be free to
				// set aint to 1 before tick 3.
				Assert.assertEquals(1, aint.get());

				t.commit();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread2() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(1);
				t.start();
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				aint.set(1);

				waitForTick(3);

				t.commit();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}
	}

	@Test
	public void earlyRelease() throws Throwable {
		TestFramework.runOnce(new EarlyRelease());

		Assert.assertEquals(2, state("x"));
		Assert.assertEquals(1, state("y"));
	}

	/**
	 * Manual early release test case.
	 * 
	 * <pre>
	 * T1 [ r(x)0 w(x)1 man_release(x) r(y)0 w(y)1 ]
	 * T2  [                           r(x)1 w(x)2 ]
	 * </pre>
	 */
	class ManualEarlyRelease extends MultithreadedTest {

		AtomicInteger aint;

		@Override
		public void initialize() {
			aint = new AtomicInteger(0);
		}

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));
				Variable y = t.accesses((Variable) registry.lookup("y"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				t.release(x);

				int vy = y.read();
				y.write(vy + 1);

				waitForTick(3);

				// If manual early release worked correctly, T2 should be free
				// to set aint to 1 before tick 3.
				Assert.assertEquals(1, aint.get());

				t.commit();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread2() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(1);
				t.start();
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				aint.set(1);

				waitForTick(3);

				t.commit();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}
	}

	@Test
	public void manualEarlyRelease() throws Throwable {
		TestFramework.runOnce(new ManualEarlyRelease());

		Assert.assertEquals(2, state("x"));
		Assert.assertEquals(1, state("y"));
	}

	/**
	 * Early release test case.
	 * 
	 * <pre>
	 * T1 [ r(x)0 w(x)1 r(y)0 w(y)1 r(z)0 r(z)1 ]
	 * T2  [            r(x)1 w(x)2 ] ----------> ]
	 * </pre>
	 * 
	 * Specifically checks whether T2 is free to operate on X while T1 operates
	 * on y.
	 */
	class CommitOrder extends MultithreadedTest {

		AtomicInteger aint;

		@Override
		public void initialize() {
			aint = new AtomicInteger(0);
		}

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				t.start();
				waitForTick(1);
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				int vy = y.read();
				y.write(vy + 1);

				int vz = z.read();
				z.write(vz + 1);

				// T2 should not be able to set aint to 2, because it should not
				// be able to commit.
				Assert.assertEquals(1, aint.get());

				t.commit();

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}

		public void thread2() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(1);
				t.start();
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				aint.set(1);

				t.commit();

				aint.set(2);

			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}
	}

	@Test
	public void commitOrder() throws Throwable {
		TestFramework.runOnce(new CommitOrder());

		Assert.assertEquals(2, state("x"));
		Assert.assertEquals(1, state("y"));
		Assert.assertEquals(1, state("z"));

	}

	/**
	 * Access after release test case.
	 */
	class AccessAfterRelease extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);

				t.start();

				int vx = x.read();
				x.write(vx + 1);
				x.write(vx + 1);

				Assert.fail("Atempting to commit after illegal post-release access.");

				t.commit();

			} catch (TransactionException e) {
				try {
					t.rollback();
				} catch (TransactionException e1) {
					Assert.fail(e1.getMessage());
				}
			} catch (Exception e) {
				Assert.fail(e.getMessage());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				Assert.fail(e.getMessage());
			}
		}
	}

	@Test
	public void accessAfterRelease() throws Throwable {
		TestFramework.runOnce(new AccessAfterRelease());

		Assert.assertEquals(0, state("x"));
		Assert.assertEquals(0, state("y"));
		Assert.assertEquals(0, state("z"));
	}

}
