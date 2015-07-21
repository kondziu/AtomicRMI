package put.unit.generic;

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
 * Access after release test case.
 */
public class AccessAfterRelease extends RMITest {
	class Threads extends MultithreadedTest {

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
	public void accessAfterRelease() throws Throwable {
		OneThreadToRuleThemAll.emergencyStart();
		OneHeartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
		Assert.assertEquals(0, state("y"));
		Assert.assertEquals(0, state("z"));
	}
}
