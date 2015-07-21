package put.unit.generic;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.optsva.RollbackForcedException;
import put.atomicrmi.optsva.Transaction;
import put.atomicrmi.optsva.sync.Heartbeat;
import put.atomicrmi.optsva.sync.TaskController;
import put.atomicrmi.optsva.sync.TransactionFailureMonitorImpl;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

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
public class ForcedRetryOnInvoke extends RMITest {
	class Threads extends MultithreadedTest {
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

				waitForTick(4);
				waitForTick(5);
				t.rollback();
				waitForTick(6);

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} 

			waitForTick(99);
			try {
				TransactionFailureMonitorImpl.getInstance().emergencyStop();
				TaskController.emergencyStop();
				Heartbeat.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}

		public void thread2() {
			Transaction t = null;
			int i = 0;
			boolean comitted = false;
			while (!comitted) {
				try {
					t = new Transaction();
					Variable x = t.accesses((Variable) registry.lookup("x"), 2);
					Variable y = t.accesses((Variable) registry.lookup("y"), 2);
					Variable z = t.accesses((Variable) registry.lookup("z"), 2);

					waitForTick(i + 1);
					System.out.println("starting");
					t.start();
					System.out.println("started");
					waitForTick(i + 2);

					int vx = x.read();
					x.write(vx + 1);

					waitForTick(i + 4);
					int vy = y.read();

					waitForTick(i + 5);
					waitForTick(i + 6);

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
					e.printStackTrace();
					throw new RuntimeException(e.getMessage(), e.getCause());
				} finally {
					i += 10;
				}
			}

			waitForTick(99);
			try {
				TransactionFailureMonitorImpl.getInstance().emergencyStop();
				TaskController.emergencyStop();
				Heartbeat.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
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
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(1, state("x"));
		Assert.assertEquals(1, state("y"));
		Assert.assertEquals(1, state("z"));
	}
}
