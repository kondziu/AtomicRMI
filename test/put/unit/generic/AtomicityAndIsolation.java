package put.unit.generic;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.optsva.Transaction;
import put.atomicrmi.optsva.sync.Heartbeat;
import put.atomicrmi.optsva.sync.TaskController;
import put.atomicrmi.optsva.sync.TransactionFailureMonitorImpl;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Atomicity and Isolation test case.
 * 
 * <pre>
 * T1 [ r(x)0 w(x)1 r(y)0 w(y)1 ]
 * T2  [                         r(x)1 w(x)2 r(y)1 w(y)2 ]
 * </pre>
 */
public class AtomicityAndIsolation extends RMITest {
	class Threads extends MultithreadedTest {

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
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(2, state("x"));
	}
}
