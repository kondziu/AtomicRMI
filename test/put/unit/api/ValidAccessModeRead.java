package put.unit.api;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.OneHeartbeat;
import put.atomicrmi.OneThreadToRuleThemAll;
import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionException;
import put.atomicrmi.TransactionFailureMonitor;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Reading in read access mode.
 */
public class ValidAccessModeRead extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.reads((Variable) registry.lookup("x"));
				t.start();
				x.read();
				t.commit();
			} catch (TransactionException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
				OneThreadToRuleThemAll.emergencyStop();
				OneHeartbeat.emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}
	}

	@Test
	public void validAccessModeRead() throws Throwable {
		OneThreadToRuleThemAll.emergencyStart();
		OneHeartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());
		Assert.assertEquals(0, state("x"));
	}
}
