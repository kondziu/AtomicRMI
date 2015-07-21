package put.atomicrmi.optsva.sync;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A single thread controlling various asynchronous
 * wait-until-condition-and-then-execute tasks in OptSVA.
 * 
 * Any task may be left to be executed. Each task is of type {@link Task} and
 * consists of two parts, each encapsulated in a method: condition and run.
 * Basically, the thread waits until it is signaled. Once signal the thread
 * checks pertinent tasks, whichever task returns true from its condition is
 * then executed in sequence.
 * 
 * The class is synchronized to maintain consistency.
 * 
 * @author Konrad Siek
 * 
 */
public class TaskController extends Thread {

	/**
	 * A task queued up for execution in the controller. The task will be
	 * executed if the thread is notified and the condition of the task
	 * subsequently returns true.
	 * 
	 * @author Konrad Siek
	 */
	public interface Task {
		/**
		 * Condition that must be met before task executes.
		 * 
		 * @param controller
		 * @return <code>true</code> if task ca proceed with execution,
		 *         <code>false</code> otherwise.
		 * @throws Exception
		 */
		boolean condition(TaskController controller) throws Exception;

		/**
		 * The body of the task, executed once the condition returns
		 * <code>true</code>/
		 * 
		 * @param controller
		 * @throws Exception
		 */
		void run(TaskController controller) throws Exception;
	}

	/**
	 * The default controller thread.
	 */
	public static final TaskController theOneThread = new TaskController("ARMI TheOneThread");

	/**
	 * Start default thread.
	 */
	static {
		theOneThread.setDaemon(true);
		theOneThread.start();
	}

	/**
	 * Restart the thread after an emergency stop.
	 * 
	 * @throws Exception
	 */
	public static synchronized void emergencyStart() throws Exception {
		if (theOneThread.isAlive())
			return;

		Field field = TaskController.class.getField("theOneThread");

		field.setAccessible(true);
		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

		field.set(null, new TaskController("ARMI TheOneThread"));
	}

	/**
	 * Stop the thread.
	 */
	public static synchronized void emergencyStop() {
		if (!theOneThread.isAlive())
			return;

		theOneThread.tasks.clear();
		theOneThread.freshTasks = false;
		theOneThread.waiting = false;
		theOneThread.interrupt();
	}

	public TaskController(String name) {
		super(name);
		tasks = new LinkedList<Task>();
	}

	/**
	 * Tasks waiting for execution.
	 */
	private final List<Task> tasks;

	/**
	 * Were there new tasks introduced after the last time conditions were
	 * checked.
	 */
	private boolean freshTasks = false;

	/**
	 * Run the thread. (Only used for emergency stop).
	 */
	private boolean run = true;

	/**
	 * The thread is waiting until further notice.
	 */
	private boolean waiting = false;

	/**
	 * Add a task pending execution.
	 * 
	 * @param task
	 */
	public synchronized void add(Task task) {
		synchronized (tasks) {
			tasks.add(task);
		}

		freshTasks = true;

		if (waiting)
			notify();
	}

	/**
	 * Notify the thread about changes that can affect conditions of tasks.
	 */
	public synchronized void ping() {
		freshTasks = true;

		if (waiting)
			notify();
	}

	@Override
	public void run() {
		List<Task> currentTasks = null;

		while (run) {
			/**
			 * If nobody is doing anything, wait for someone to deposit some
			 * work or ping.
			 */
			synchronized (this) {
				try {
					if (!freshTasks) { // TODO
						waiting = true;
						wait();
						waiting = false;
					}
				} catch (InterruptedException e) {
					// Intentionally left blank.
					if (!run) {
						synchronized (tasks) {
							tasks.clear();
						}

						freshTasks = false;
						break;
					}
				}
			}

			/**
			 * Somebody woke us up, we see whom it was, and check his task.
			 */
			synchronized (tasks) {
				currentTasks = new LinkedList<Task>(tasks);
				freshTasks = false;
			}

			final Iterator<Task> taskIter = currentTasks.iterator();
			while (taskIter.hasNext()) {
				final Task task = taskIter.next();
				try {
					if (task.condition(this))
						task.run(this);
					else
						taskIter.remove();
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage(), e.getCause());
				}
			}

			synchronized (tasks) {
				tasks.removeAll(currentTasks);
			}

			/**
			 * Aaand... go back to waiting.
			 */
		}
	}
}
