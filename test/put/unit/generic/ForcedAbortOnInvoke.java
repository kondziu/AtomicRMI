package put.unit.generic;

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
 * Forced abort test case.
 * 
 * <pre>
 * T1 [   r(x)0 w(x)1              !              
 * T2   [             r(x)1 w(x)2    !r(y)1 !r(y)2 !r(z)1 !r(z)2
 * </pre>
 * 
 * One of the operations in T2 should abort.
 */
public class ForcedAbortOnInvoke extends RMITest {
	class Threads extends MultithreadedTest {
		public void thread1() {
			Transaction t = null;
			try {
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				@SuppressWarnings("unused")
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				t.start();
				waitForTick(1);
				waitForTick(2);

				int vx = x.read();
				x.write(vx + 1);

				waitForTick(3);
				int vy = y.read();
				y.write(vy + 1);
				waitForTick(4);

				t.rollback();
				waitForTick(5);

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
				int v;
				t = new Transaction();
				Variable x = t.accesses((Variable) registry.lookup("x"), 2);
				Variable y = t.accesses((Variable) registry.lookup("y"), 2);
				Variable z = t.accesses((Variable) registry.lookup("z"), 2);

				waitForTick(1);
				t.start();
				waitForTick(2);

				v = x.read();
				x.write(v + 1);

				waitForTick(3);
				v = y.read();
				waitForTick(4);
				waitForTick(5);
				y.write(v + 1);

				v = z.read();
				z.write(v + 1);

				Assert.fail("Transaction attempted to commit, when it should have aborted on invoke.");
				t.commit();

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
	}

	/**
	 * Forced abort test case.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void forcedAbortOnInvoke() throws Throwable {
		TaskController.emergencyStart();
		Heartbeat.emergencyStart();
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
		Assert.assertEquals(0, state("y"));
		Assert.assertEquals(0, state("z"));
	}
}
