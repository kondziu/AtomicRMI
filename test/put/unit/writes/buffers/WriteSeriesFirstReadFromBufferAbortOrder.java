package put.unit.writes.buffers;

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
 * Read from buffer after a series of buffered writes test case.
 * 
 * <pre>
 * T1 [ r(x)0       w(x)1 						]
 * T2  [      w(x)2        w(x)3 r(x)3 r(x)3 -----!
 * </pre>
 * 
 * Checks whether consecutive writes to a buffer work correctly and whether
 * reads synchronize with them correctly. finally checks whether aborts roll
 * back to appropriate state and maintain commit order.
 */
public class WriteSeriesFirstReadFromBufferAbortOrder extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				int v1 = x.read();
				Assert.assertEquals(0, v1);

				waitForTick(3);

				waitForTick(4);
				x.write(1);

				waitForTick(13);

				t.commit();

				waitForTick(14);

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
				t.start();
				waitForTick(2);

				waitForTick(3);
				x.write(2);
				x.write(3);

				waitForTick(4);

				waitForTick(5);

				int v1 = x.read();
				Assert.assertEquals(3, v1);

				int v2 = x.read();
				Assert.assertEquals(3, v2);

				waitForTick(12);

				t.rollback();

				waitForTick(13);

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

	@Test
	public void writeSeriesFirstReadFromBufferAbortOrder() throws Throwable {
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(1, state("x"));
	}
}
