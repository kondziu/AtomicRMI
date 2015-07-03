package put.unit.reads;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.OneThreadToRuleThemAll;
import put.atomicrmi.RollbackForcedException;
import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionFailureMonitor;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Read-only cascading abort.
 * 
 * <pre>
 * T1 [ w(x)1                    !
 * T2  [      r(x)1               !r  #forced
 * T3  [            r(x)1 w(x)2     !  #forced
 * </pre>
 * 
 * Checks whether a read-only transaction reacts to forced aborts and continues
 * the cascade in following transactions.
 */
public class ReadOnlyCascadingAbort extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 1);

				t.start();
				waitForTick(1);
				waitForTick(2);
				waitForTick(3);

				x.write(1);

				waitForTick(4);

				waitForTick(10);

				t.rollback();

				waitForTick(11);

				// waitForTick(5);

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
				OneThreadToRuleThemAll.theOneThread.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}

		public void thread2() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.reads((Variable) registry.lookup("x"));

				waitForTick(1);
				t.start();
				waitForTick(2);
				waitForTick(3);

				waitForTick(4);
				int v1 = x.read();
				Assert.assertEquals(1, v1);

				waitForTick(5);
				waitForTick(6);

				waitForTick(11);
				t.commit();
				waitForTick(12);
				Assert.fail("Transaction comitted when it should have aborted");

			} catch (RollbackForcedException e) {
				// everything is fine
				waitForTick(7);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
				OneThreadToRuleThemAll.theOneThread.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}

		public void thread3() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(3);
				t.start();

				waitForTick(6);

				int v = x.read();
				Assert.assertEquals(1, v);
				x.write(v + 1);

				waitForTick(7);

				waitForTick(12);
				t.commit();
				Assert.fail("Transaction comitted when it should have aborted");

			} catch (RollbackForcedException e) {
				// everything is fine
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
				OneThreadToRuleThemAll.theOneThread.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}
	}

	@Test
	public void readOnlyCascadingAbort() throws Throwable {
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
	}
}
