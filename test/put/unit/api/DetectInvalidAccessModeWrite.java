package put.unit.api;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.OneThreadToRuleThemAll;
import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionException;
import put.atomicrmi.TransactionFailureMonitor;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Writing in read access mode.
 */
public class DetectInvalidAccessModeWrite extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.writes((Variable) registry.lookup("x"));
				t.start();
				x.read();
				Assert.fail("Atempting to commit after illegal access.");
				t.commit();
			} catch (TransactionException e) {
				try {
					t.rollback();
				} catch (TransactionException e1) {
					Assert.fail(e1.getMessage());
				}
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
	public void detectInvalidAccessModeWrite() throws Throwable {
		TestFramework.runOnce(new Threads());
		Assert.assertEquals(0, state("x"));
	}
}
