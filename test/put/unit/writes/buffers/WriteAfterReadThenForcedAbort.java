package put.unit.writes.buffers;

import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicInteger;

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
 * 
 * Write after read (after first write) then attempt commit and abort test case.
 * 
 * <pre>
 * T1 [ r(x)0       w(x)1  					    !
 * T2  [      w(x)2        r(x)2 w(x)3  r(x)3  --! #forced
 * </pre>
 * 
 * Checks whether a commit will duly fail and a forced abort will clean up.
 * Commit of T2 is <b>NOT</b> blocked here until T1 commits.
 */
public class WriteAfterReadThenForcedAbort extends RMITest {
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
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);

				t.start();
				// System.err.println("T1");
				waitForTick(1);
				waitForTick(2);
				// System.err.println("T1 2");

				int v1 = x.read();
				Assert.assertEquals(0, v1);

				waitForTick(3);
				// System.err.println("T1 3");

				waitForTick(4);
				// System.err.println("T1 4");
				x.write(1);

				waitForTick(5);
				// System.err.println("T1 5");

				waitForTick(6);
				// System.err.println("T1 6");

				waitForTick(7);
				// System.err.println("T1 7");

				waitForTick(8);
				// System.err.println("T1 8");

				waitForTick(9);
				// System.err.println("T1 9");

				// Thread.sleep(1000);
				// System.err.println("1111");
				Assert.assertEquals("T2 attempts to commit before this point.", 2, aint.get());
				t.rollback();

				waitForTick(10);

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
				// System.err.println("     T2 2");

				waitForTick(3);
				// System.err.println("     T2 3");
				x.write(2);

				waitForTick(4);
				// System.err.println("     T2 4");

				waitForTick(5);
				// System.err.println("     T2 5");

				int v1 = x.read();
				Assert.assertEquals(2, v1);

				waitForTick(6);
				// System.err.println("     T2 6");

				x.write(3);

				waitForTick(7);
				// System.err.println("     T2 7");

				int v2 = x.read();
				Assert.assertEquals(3, v2);

				waitForTick(8);
				// System.err.println("     T2 8");

				// System.err.println("2222");
				aint.set(2);
				// System.err.println("2222");

				t.commit();
				Assert.fail("Transaction comitted when it should have aborted");

				waitForTick(9);
				// System.err.println("     T2 9");
				waitForTick(10);

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
	public void writeAfterReadThenForcedAbort() throws Throwable {
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
	}
}
