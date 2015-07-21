package put.unit.writes.buffers;

import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.RollbackForcedException;
import put.atomicrmi.Transaction;
import put.atomicrmi.sync.Heartbeat;
import put.atomicrmi.sync.TaskController;
import put.atomicrmi.sync.TransactionFailureMonitorImpl;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Read from buffer after buffered write commit order, attempt commit and fail
 * test case.
 * 
 * <pre>
 * T1 [ r(x)0       w(x)1         !
 * T2  [      w(x)2        r(x)2 ---! #forced
 * </pre>
 * 
 * Checks whether a commit will duly fail and a forced abort will clean up.
 * Commit of T2 is <b>NOT</b> blocked here until T1 commits.
 */
public class WriteFirstReadFromBufferOnceForcedAbort extends RMITest {
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
				waitForTick(1);
				waitForTick(2);

				int v1 = x.read();
				Assert.assertEquals(0, v1);

				waitForTick(3);

				waitForTick(4);
				x.write(1);

				waitForTick(9);

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
				Variable x = t.accesses((Variable) registry.lookup("x"));

				waitForTick(1);
				t.start();
				waitForTick(2);

				waitForTick(3);
				x.write(2);

				waitForTick(5);
				int v1 = x.read();
				Assert.assertEquals(2, v1);

				int v2 = x.read();
				Assert.assertEquals(2, v2);

				waitForTick(8);

				aint.set(2);
				t.commit();
				Assert.fail("Transaction comitted when it should have aborted");

				waitForTick(9);

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
	public void writeFirstReadFromBufferOnceForcedAbort() throws Throwable {
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
	}
}
