package put.unit.reads;

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
 * Read-only release at start test case.
 * 
 * <pre>
 * T1 [              r(x)0 ]r
 * T2  [ r(x)1 w(x)2       ]
 * </pre>
 * 
 * Specifically checks whether T2 is free to operate on x <b>before</b> T1 even
 * touches x.
 */
public class ReadOnlyReleaseAtStart extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.reads((Variable) registry.lookup("x"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				waitForTick(3);

				// If T1 was released by T1 then T2 will not have read x yet and
				// it wouldn't have set aint to 2 yet. (Also, if x is not
				// released by T1, then tick 4 will never be reached, so the
				// point is moot).
				int v1 = x.read();
				Assert.assertEquals(0, v1);

				waitForTick(4);

				t.commit();

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} 

			// System.out.println("XX1");

			waitForTick(99);

			// System.out.println("XY1");
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

				// If T1 did not release x at start, then T2 will deadlock here
				// waiting for x to be released, and never progress to tick 3.
				int vx = x.read();
				x.write(vx + 1);

				waitForTick(3);
				waitForTick(4);

				t.commit();

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} 

			// System.out.println("XX2");

			waitForTick(99);

			// System.out.println("XY2");
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
	public void readOnlyReleaseAtStart() throws Throwable {
		// System.out.println("START5");
		// for (Thread i : Thread.getAllStackTraces().keySet()) {
		// System.out.println(i.getName());
		// }
		// System.out.println();

		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		// System.out.println("POST5");
		// for (Thread i : Thread.getAllStackTraces().keySet()) {
		// System.out.println(i.getName());
		// }
		// System.out.println();

		Assert.assertEquals(1, state("x"));
	}
}
