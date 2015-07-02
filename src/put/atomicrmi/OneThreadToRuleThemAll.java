package put.atomicrmi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OneThreadToRuleThemAll extends Thread {

	public static final OneThreadToRuleThemAll theOneThread = new OneThreadToRuleThemAll();

	static {
		theOneThread.start();
	}

	private final Map<Object, Set<Task>> tasks = new HashMap<Object, Set<Task>>();

	private final Set<Object> activeCategories = new HashSet<Object>();

	/**
	 * Controller thread is waiting, as opposed to executing some task.
	 */
	// private boolean waiting = false;

	public interface Task {
		boolean condition(OneThreadToRuleThemAll controller) throws Exception;

		void run(OneThreadToRuleThemAll controller) throws Exception;

		// Object category();
	}

	public synchronized boolean add(Object category, Task task) {
		Set<Task> set = tasks.get(category);
		if (set == null) {
			set = new HashSet<Task>();
			tasks.put(category, set);
		}

		set.add(task);

		activeCategories.add(category);
		notify();

		return true;
	}

	public synchronized void ping(Object category) {
		activeCategories.add(category);
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
					if (activeCategories.isEmpty())
						wait();
				} catch (InterruptedException e) {
					// Intentionally left blank.
				}
			}

			/**
			 * Somebody woke us up, we see whom it was, and check his task.
			 */
			synchronized (this) {
				for (Object category : activeCategories) {
					for (Task task : tasks.get(category)) {
						try {
							if (task.condition(this))
								task.run(this);
						} catch (Exception e) {
							throw new RuntimeException(e.getMessage(), e.getCause());
						}
					}
				}
			}

			/**
			 * Aaand... go back to waiting.
			 */
		}
	}
}
