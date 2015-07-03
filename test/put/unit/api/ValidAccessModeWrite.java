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
 * Writing in write access mode.
 */
public class ValidAccessModeWrite extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.writes((Variable) registry.lookup("x"));
				t.start();
				x.write(1);
				t.commit();
			} catch (TransactionException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
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
	public void validAccessModeWrite() throws Throwable {
		TestFramework.runOnce(new Threads());
		Assert.assertEquals(1, state("x"));
	}
}
