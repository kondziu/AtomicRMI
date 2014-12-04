package put.unit.writes.writeonly;

import java.rmi.RemoteException;

import org.junit.Assert;
import org.junit.Test;

import put.atomicrmi.Transaction;
import put.atomicrmi.TransactionFailureMonitor;
import put.atomicrmi.Update;
import put.unit.RMITest;
import put.unit.vars.Variable;
import edu.umd.cs.mtc.MultithreadedTest;
import edu.umd.cs.mtc.TestFramework;

/**
 * Single write-only transaction with a single write that aborts.
 * 
 * <pre>
 * T1 [  w(x)1  !
 * </pre>
 */
public class SingleWriteOnlyTransactionAborting extends RMITest {
	class Threads extends MultithreadedTest {

		public void thread1() {
			Transaction t = null;
			try {
				t = new Update();
				Variable x = t.accesses((Variable) registry.lookup("x"));
				Variable y = t.accesses((Variable) registry.lookup("y"));

				t.start();
		
				x.write(1);
				x.write(2);
				y.write(1);

				t.rollback();

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			} finally {
				t.stopHeartbeat();
			}

			waitForTick(99);
			try {
				TransactionFailureMonitor.getInstance().emergencyStop();
			} catch (RemoteException e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e.getCause());
			}
		}
	}

	@Test
	public void singleWriteOnlyTransaction() throws Throwable {
		TestFramework.runOnce(new Threads());

		Assert.assertEquals(0, state("x"));
		Assert.assertEquals(0, state("y"));
	}
}
