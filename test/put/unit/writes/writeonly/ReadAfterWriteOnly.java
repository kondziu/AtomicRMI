package put.unit.writes.writeonly;

import java.rmi.RemoteException;

import org.junit.Assert;

import put.atomicrmi.optsva.Transaction;
import put.atomicrmi.optsva.Update;
import put.atomicrmi.optsva.sync.Heartbeat;
import put.atomicrmi.optsva.sync.TaskController;
import put.atomicrmi.optsva.sync.TransactionFailureMonitorImpl;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;

/**
 * R/W transaction after a write-only transaction.
 * 
 * FIXME Test does not work, since it is impossible to schedule things inside a
 * commit with ticks. It seems to work when it does schedule correctly by
 * accident, but it does so only rarely.
 * 
 * <pre>
 *     [  w(x)1 w(x)2 w(y)1 ]
 *      [ ------------------r(x)2 w(x)3 ]
 * </pre>
 * 
 */
public class ReadAfterWriteOnly extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Update();
				Variable x = t.accesses((Variable) registry.lookup("x"));
				Variable y = t.accesses((Variable) registry.lookup("y"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				x.write(1);

				x.write(2);

				y.write(1);

				waitForTick(3);
				t.commit();
				waitForTick(4);

				waitForTick(7);
				waitForTick(8);

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
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(1);
				// t.start();
				waitForTick(2);

				waitForTick(3);
				t.start();
				waitForTick(4);

				waitForTick(5);

				int v1 = x.read();
				Assert.assertEquals(2, v1);

				x.write(v1 + 1);

				waitForTick(7);

				t.commit();

				waitForTick(8);

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
	}

	// @Test
	// public void writeFirstReadFromBuffer() throws Throwable {
//	OneThreadToRuleThemAll.emergencyStart();
	// TestFramework.runOnce(new Threads());
	//
	// Assert.assertEquals(3, state("x"));
	// Assert.assertEquals(1, state("y"));
	// }
}
