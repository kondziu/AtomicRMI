package put.unit;

import java.util.concurrent.Callable;

import edu.umd.cs.mtc.MultithreadedTest;

public class InvasiveMultithreadedTest extends MultithreadedTest {
	protected Callable<Object> createWaiterForTick(final int... n) {
		return new Callable<Object>() {
			public Object call() throws Exception {
				for (int i : n) {
					waitForTick(i);
				}
				return null;
			}
		};
	}
}
