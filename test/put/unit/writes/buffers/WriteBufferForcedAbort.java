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
 * Buffered write then attempt commit and fail.
 * 
 * <pre>
 * T1 [ r(x)0       w(x)1  !
 * T2  [      w(x)2       ---! #forced
 * </pre>
 * 
 * Checks whether a commit will duly fail and a forced abort will clean up.
 * Commit of T2 is <b>NOT</b> blocked here until T1 commits.
 */
public class WriteBufferForcedAbort extends RMITest {
	class Threads extends MultithreadedTest {

		AtomicInteger aint;

		@Override
		public void initialize() {
			aint = new AtomicInteger(0);
		}

		// FIXME !!!
		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);

				t.start();
				waitForTick(1);
				waitForTick(2);
				System.err.println("T1 2");

				int v1 = x.read();
				Assert.assertEquals(0, v1);

				waitForTick(3);
				System.err.println("T1 3");

				waitForTick(4);
				System.err.println("T1 4");
				x.write(1);

				waitForTick(5);
				System.err.println("T1 5");

				waitForTick(6);
				System.err.println("T1 6");

				Assert.assertEquals("T2 attempts to commit before this point.", 2, aint.get());
				t.rollback();

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
				Variable x = t.writes((Variable) registry.lookup("x"));

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

				aint.set(2);
				t.commit();
				Assert.fail("Transaction comitted when it should have aborted");

				waitForTick(6);

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
	public void writeBufferForcedAbort() throws Throwable {
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
	}
}
