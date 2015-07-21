package put.unit.reads;

import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicInteger;

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
 * Read-only buffered read test case.
 * 
 * <pre>
 * T1 [ r(x)0 r(x)0 r(x)0 r(x)0 ]r
 * T2  [      r(x)1 w(x)2       ]
 * </pre>
 * 
 * Specifically checks whether T2 is free to operate on x while T1 operates on x
 * at the same time, and whether T1 sees a consistent value of x regardless.
 */
public class ReadOnlyBuferredRead extends RMITest {
	class Threads extends MultithreadedTest {

		AtomicInteger aint;

		@Override
		public void initialize() {
			aint = new AtomicInteger(0);
		}

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.reads((Variable) registry.lookup("x"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				int v1 = x.read();
				Assert.assertEquals(0, v1);

				waitForTick(3);
				int v2 = x.read();
				Assert.assertEquals(0, v2);

				// If T1 was released by T1 then T2 will not have read x yet and
				// it wouldn't have set aint to 2 yet. (Also, if x is not
				// released by T1, then tick 4 will never be reached, so the
				// point is moot).
				waitForTick(4);
				Assert.assertEquals("Read-only variable released.", 2, aint.get());

				int v3 = x.read();
				Assert.assertEquals(0, v3);

				waitForTick(5);
				int v4 = x.read();
				Assert.assertEquals(0, v4);

				waitForTick(6);

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

		public void thread2() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(1);
				t.start();
				waitForTick(2);

				waitForTick(3);
				int vx = x.read();
				aint.set(2);

				waitForTick(4);
				x.write(vx + 1);

				waitForTick(6);

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
	}

	@Test
	public void readOnlyBufferedRead() throws Throwable {
//		System.out.println("START4");		
//		for (Thread i : Thread.getAllStackTraces().keySet()) {
//			System.out.println(i.getName());
//		}
//		System.out.println();
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());
		
//		System.out.println("POST4");		
//		for (Thread i : Thread.getAllStackTraces().keySet()) {
//			System.out.println(i.getName());
//		}
//		System.out.println();

		Assert.assertEquals(1, state("x"));
	}
}
