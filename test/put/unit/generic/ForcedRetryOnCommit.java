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
 * Forced abort and retry test case.
 * 
 * <pre>
 * T1 [   r(x)0 w(x)1              r(y)0 r(y)1 r(z)0 r(z)1              !
 * T2   [             r(x)1 w(x)2              r(y)1 r(y)2 r(z)1 r(z)2    ! ... [ r(x)0 w(x)1 ...
 * </pre>
 */
public class ForcedRetryOnCommit extends RMITest {
	class Threads extends MultithreadedTest {
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
	 * Forced abort and retry test case.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void forcedRetryOnCommit() throws Throwable {
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(1, state("x"));
		Assert.assertEquals(1, state("y"));
		Assert.assertEquals(1, state("z"));
	}
}
