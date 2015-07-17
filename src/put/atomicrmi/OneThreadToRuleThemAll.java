package put.atomicrmi;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class OneThreadToRuleThemAll extends Thread {

	public static final OneThreadToRuleThemAll theOneThread = new OneThreadToRuleThemAll("ARMI TheOneThread");

	public static synchronized void emergencyStart() throws Exception {
		if (theOneThread.isAlive())
			return;
		
		Field field = OneThreadToRuleThemAll.class.getField("theOneThread");
		
		field.setAccessible(true);
		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
		
		field.set(null, new OneThreadToRuleThemAll("ARMI TheOneThread"));
	}
	
	public static synchronized void emergencyStop() {
		if (!theOneThread.isAlive()) {
			return;
		}
		// run = false;
		theOneThread.tasks.clear();
		theOneThread.freshTasks = false;
		theOneThread.waiting = false;
		theOneThread.interrupt();
	}
	
	// public synchronized static final void reboot() {
	// if (!theOneThread.run) {
	// theOneThread.run = true;
	// theOneThread.freshTasks = false;
	// theOneThread.tasks.clear();
	// theOneThread.waiting = false;
	// theOneThread.setDaemon(true);
	// theOneThread.start();
	// }
	// }

	static {
		theOneThread.setDaemon(true);
		theOneThread.start();
	}

	public OneThreadToRuleThemAll(String name) {
		super(name);
	}

	private final List<Task> tasks = new LinkedList<Task>();

	private boolean freshTasks = false;

	// private final Boolean[] activeCategories = new
	// Boolean[Category.values().length];

	// private int taskCount = 0;

	private boolean run = true;

	private boolean waiting;

	// private boolean interrupted = false;

	/**
	 * Controller thread is waiting, as opposed to executing some task.
	 */
	// private boolean waiting = false;

	public interface Task {
		boolean condition(OneThreadToRuleThemAll controller) throws Exception;

		void run(OneThreadToRuleThemAll controller) throws Exception;

		// Object category();
	}

	public synchronized boolean add(Task task) {
		System.out.println("Adding task " + task);

		// Set<Task> set = tasks.get(category);
		// if (set == null) {
		// set = new HashSet<Task>();
		// tasks.put(category, set);
		// }

		synchronized (tasks) {
			tasks.add(task);
		}
		// taskCount++;
		freshTasks = true;

		// activeCategories[category.ordinal()] = true;
		if (waiting)
			notify();

		return true;
	}

	public synchronized void ping() {
//		System.out.println("Pinging");
		freshTasks = true;

		// activeCategories[category.ordinal()] = true;
		if (waiting)
			notify();
	}


	// private boolean activeCategoriesPresent() {
	// for (Set<Object> category : activeCategories) {
	// if (!category.isEmpty())
	// return true;
	// }
	// return false;
	// }

	// boolean allCategoriesInactive() {
	// for (boolean active : activeCategories) {
	// if (active) {
	// return false;
	// }
	// }
	// return true;
	// }

	@Override
	public void run() {

		// List<Category> checkedCategories = new LinkedList<Category>();

		List<Task> currentTasks;

		while (run) {
			// try {
			// sleep(1);
			// } catch (InterruptedException e1) {
			// if (!run) {
			// // interrupted = true;
			// tasks.clear();
			// freshTasks = false;
			// break;
			// }
			// }
//			System.out.println("Looping in OneThread");

			/**
			 * If nobody is doing anything, wait for someone to deposit some
			 * work or ping.
			 */
			synchronized (this) {
				try {
//					System.out.println("Fresh tasks? " + freshTasks);
					if (!freshTasks) { // TODO
//						System.out.println("Waiting in OneThread");
						waiting = true;
						wait();
						waiting = false;
					} //else {
						// //
//						System.out.println("Not waiting in OneThread - active categories present");
//					}
				} catch (InterruptedException e) {
					// Intentionally left blank.
//					System.out.println("OneThread interrupted");
					if (!run) {
						// interrupted = true;
						synchronized (tasks) {
							tasks.clear();
						}
						freshTasks = false;
						break;
					}
				}

				// for (int i = 0; i < activeCategories.length; i++) {
				// checkedCategories.add(activeCategories[i]);
				// }
			}

//			System.out.println("Activating OneThread");

			/**
			 * Somebody woke us up, we see whom it was, and check his task.
			 */
			// synchronized (this) {
			// for (Category category : checkedCategories) {
			// Object category = categoryIterator.next();
			// categoryIterator.remove();

			// System.out.println("Going through category " + category + ": " +
			// tasks.get(category));

			// while (freshTasks) {
			synchronized (tasks) {
				currentTasks = new LinkedList<Task>(tasks);
				// tasks.clear();
				freshTasks = false;
			}

			Iterator<Task> taskIter = currentTasks.iterator();
			while (taskIter.hasNext()) {
				Task task = taskIter.next();
//				System.out.println("Going through task " + task);
				try {
					if (task.condition(this)) {
//						System.out.println("Condition met for task " + task);
						task.run(this);
						// taskCount--;
					} else {
//						System.out.println("Condition not met for task " + task);
						// only preserve the task whose conditions were met
						taskIter.remove();
					}
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage(), e.getCause());
				}
			}

			synchronized (tasks) {
				// currentTasks = new LinkedList<Task>(tasks);
				tasks.removeAll(currentTasks);
			}
			// }

			// System.out.println("OneThread going to sleep");

			/**
			 * Aaand... go back to waiting.
			 */
		}
	}
}
