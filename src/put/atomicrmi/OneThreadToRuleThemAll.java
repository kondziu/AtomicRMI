package put.atomicrmi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import put.atomicrmi.OneThreadToRuleThemAll.Task;

public class OneThreadToRuleThemAll extends Thread {

	public static final OneThreadToRuleThemAll theOneThread = new OneThreadToRuleThemAll();

	static {
		theOneThread.start();
	}

	private final Map<Object, Set<Task>> tasks = new HashMap<Object, Set<Task>>();

	private final Set<Object> activeCategories = new HashSet<Object>();

	private int taskCount = 0;

	private boolean run = true;

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
		System.out.println("Adding " + category + " task " + task);

		Set<Task> set = tasks.get(category);
		if (set == null) {
			set = new HashSet<Task>();
			tasks.put(category, set);
		}

		set.add(task);
		taskCount++;

		activeCategories.add(category);
		notify();

		return true;
	}

	public synchronized void ping(Object category) {
		System.out.println("Pinging " + category);

		activeCategories.add(category);
		notify();
	}
	
	public synchronized void emergencyStop() {
		if (!isAlive()) {
			return;
		}
		run  = false;
		interrupt();
	}

	// private boolean activeCategoriesPresent() {
	// for (Set<Object> category : activeCategories) {
	// if (!category.isEmpty())
	// return true;
	// }
	// return false;
	// }

	@Override
	public void run() {

		while (run) {
			System.out.println("Looping in OneThread");

			/**
			 * If nobody is doing anything, wait for someone to deposit some
			 * work or ping.
			 */
			synchronized (this) {
				try {
					if (taskCount == 0 || activeCategories.isEmpty()) { // TODO
						System.out.println("Waiting in OneThread");
						wait();
					} else {
						System.out.println("Not waiting in OneThread - active categories present");
					}
				} catch (InterruptedException e) {
					// Intentionally left blank.
					System.out.println("OneThread interrupted");
					if (!run) {
						break;
					}
				}
			}

			System.out.println("Activating OneThread");

			/**
			 * Somebody woke us up, we see whom it was, and check his task.
			 */
			// synchronized (this) {
			Iterator<Object> categoryIterator = activeCategories.iterator();
			while (categoryIterator.hasNext()) {
				Object category = categoryIterator.next();
				categoryIterator.remove();

				System.out.println("Going through category " + category + ": " + tasks.get(category));

				Iterator<Task> taskIterator = tasks.get(category).iterator();
				while (taskIterator.hasNext()) {
					Task task = taskIterator.next();
					System.out.println("Going through category " + category + " task " + task);
					try {
						if (task.condition(this)) {
							System.out.println("Condition met for task " + task);
							taskIterator.remove();
							task.run(this);
							taskCount--;
						} else {
							System.out.println("Condition not met for task " + task);
						}
					} catch (Exception e) {
						throw new RuntimeException(e.getMessage(), e.getCause());
					}
				}
			}
			// }

			System.out.println("OneThread going to sleep");

			/**
			 * Aaand... go back to waiting.
			 */
		}
	}
}
