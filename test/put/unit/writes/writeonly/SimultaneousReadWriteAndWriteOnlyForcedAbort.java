package put.unit.writes.writeonly;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.optsva.RollbackForcedException;
import put.atomicrmi.optsva.Transaction;
import put.atomicrmi.optsva.Update;
import put.atomicrmi.optsva.sync.Heartbeat;
import put.atomicrmi.optsva.sync.TaskController;
import put.atomicrmi.optsva.sync.TransactionFailureMonitorImpl;
import put.unit.InvasiveMultithreadedTest;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.TestFramework;

/**
 * R/W transaction simultaneous with a write-only transaction, write-only is
 * forced to abort because R/W aborts.
 * 
 * <pre>
 *     [  w(x)2 w(x)3 w(y)1        5--6-!
 *      [              r(x)0 w(x)1  5!
 * </pre>
 * 
 * T2 has to release early. T1 has to update x before T2 aborts.
 */
public class SimultaneousReadWriteAndWriteOnlyForcedAbort extends RMITest {
	class Threads extends InvasiveMultithreadedTest {

		public void thread1() {
			Update t = null;
			try {
				t = new Update();
				Variable x = t.accesses((Variable) registry.lookup("x"));
				Variable y = t.accesses((Variable) registry.lookup("y"));

				t.start();
				waitForTick(1);
				waitForTick(2);

				x.write(2);

				x.write(3);

				y.write(1);

				waitForTick(4);
				t.commitInterrupted(createWaiterForTick(5), createWaiterForTick(6));

				waitForTick(7);
				waitForTick(8);

				Assert.fail("Transaction comitted when it should have aborted");

			} catch (RollbackForcedException e) {
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
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);

				waitForTick(1);
				t.start();
				waitForTick(2);

				int v1 = x.read();
				Assert.assertEquals(0, v1);

				x.write(v1 + 1); // early release

				waitForTick(3);
				waitForTick(5);
				waitForTick(6);
				t.rollback();
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
	}

	@Test
	public void simultaneousReadWriteAndWriteOnlyForcedAbort() throws Throwable {
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
		Assert.assertEquals(0, state("y"));
	}
}
