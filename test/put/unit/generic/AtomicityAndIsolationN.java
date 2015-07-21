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
 * T2  [            r(x)1 w(x)2 r(y)1 w(y)2 ]
 * T3   [                        r(x)1 w(x)2 r(y)1 w(y)2 ]
 * T4    [                                   r(x)1 w(x)2 r(y)1 w(y)2 ]
 * T5     [                                              r(x)1 w(x)2 r(y)1 w(y)2 ]
 * T6      [                                                         r(x)1 w(x)2 r(y)1 w(y)2 ]
 * </pre>
 */
public class AtomicityAndIsolationN extends RMITest {
	class Threads extends MultithreadedTest {

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
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(6, state("x"));
		Assert.assertEquals(6, state("y"));
	}
}
