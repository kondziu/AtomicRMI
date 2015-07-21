package put.unit.reads;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.optsva.RollbackForcedException;
import put.atomicrmi.optsva.Transaction;
import put.atomicrmi.optsva.sync.Heartbeat;
import put.atomicrmi.optsva.sync.TaskController;
import put.atomicrmi.optsva.sync.TransactionFailureMonitorImpl;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Read-only cascading abort.
 * 
 * <pre>
 * T1 [ w(x)1                    !
 * T2  [      r(x)1               !r  #forced
 * T3  [            r(x)1 w(x)2     !  #forced
 * </pre>
 * 
 * Checks whether a read-only transaction reacts to forced aborts and continues
 * the cascade in following transactions.
 */
public class ReadOnlyCascadingAbort extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 1);

				t.start();
				waitForTick(2);
				waitForTick(3);

				// System.out.println("X1");

				x.write(1);

				// System.out.println("X2");

				waitForTick(4);

				waitForTick(10);

				// System.out.println("X3");

				t.rollback();

				// System.out.println("X4");

				waitForTick(11);

				// waitForTick(5);

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
				Variable x = t.reads((Variable) registry.lookup("x"));

				waitForTick(1);
				t.start();
				waitForTick(2);
				waitForTick(3);

				// System.out.println("Y1");

				waitForTick(4);

				// System.out.println("Y11");
				int v1 = x.read();
				Assert.assertEquals(1, v1);

				// System.out.println("Y2");

				waitForTick(5);
				waitForTick(6);

				// System.out.println("Y3");

				waitForTick(11);

				// System.out.println("Y4");
				t.commit();
				waitForTick(12);
				Assert.fail("Transaction comitted when it should have aborted");

			} catch (RollbackForcedException e) {
				// everything is fine
				waitForTick(7);
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

		public void thread3() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(3);

				// System.out.println("Z0");

				t.start();

				// System.out.println("Z1");

				waitForTick(6);

				int v = x.read();
				// System.out.println("Z2");
				Assert.assertEquals(1, v);
				x.write(v + 1);

				waitForTick(7);

				waitForTick(12);
				t.commit();
				Assert.fail("Transaction comitted when it should have aborted");

			} catch (RollbackForcedException e) {
				// everything is fine
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
	public void readOnlyCascadingAbort() throws Throwable {

		// System.out.println("START");
		// for (Thread i : Thread.getAllStackTraces().keySet()) {
		// System.out.println(i.getName());
		// }
		// System.out.println();
		//
		// try {
		// OneThreadToRuleThemAll.reboot();
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());
		// } catch (Throwable t) {
		//
		// throw t;
		// } finally {
		// System.out.println("POST");
		// Map<Thread, StackTraceElement[]> th = Thread.getAllStackTraces();
		// for (Thread i : th.keySet()) {
		// System.out.println(i.getName());
		// for (StackTraceElement j : th.get(i)) {
		// System.out.println("   " + j.getClassName() + "    " +
		// j.getMethodName() + " " + j.getLineNumber());
		// }
		// System.out.println();
		// }
		// System.out.println();
		// }

		Assert.assertEquals(0, state("x"));
	}
}
