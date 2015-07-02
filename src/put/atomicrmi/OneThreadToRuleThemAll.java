package put.atomicrmi;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class OneThreadToRuleThemAll extends Thread {

	public static final OneThreadToRuleThemAll theOneThread = new OneThreadToRuleThemAll();

	static {
		theOneThread.start();
	}

	private final Map<Object, Task> tasks = new HashMap<Object, Task>();

	private final List<Object> activeParents = new LinkedList<Object>();

	/**
	 * Controller thread is waiting, as opposed to executing some task.
	 */
//	private boolean waiting = false;

	public interface Task {
		boolean condition(OneThreadToRuleThemAll controller);

		void run(OneThreadToRuleThemAll controller);
	}

	public synchronized boolean add(Object parent, Task task) {
		if (tasks.containsKey(parent)) {
			return false;
		}

		tasks.put(parent, task);

		activeParents.add(parent);
		notify();

		return true;
	}

	public synchronized void ping(Object parent) {
		activeParents.add(parent);
		notify();
	}

	@Override
	public void run() {

		while (true) {
			/**
			 * If nobody is doing anything, wait for someone to deposit some
			 * work or ping.
			 */
			synchronized (this) {
				try {
					if (activeParents.isEmpty()) {
						// TODO synchronize around assignment?
//						waiting = true;

						wait();

						// TODO synchronize around assignment?
//						waiting = false;
					}
				} catch (InterruptedException e) {
					// Intentionally left blank.
				}
			}

			/**
			 * Somebody woke us up, we see whom it was, and check his task.
			 */
			synchronized (this) {
				for (Object parent : activeParents) {
					Task task = tasks.get(parent);
					if (task.condition(this)) {
						task.run(this);
					}
				}
			}

			/**
			 * Go back to waiting.
			 */
		}
	}
}
