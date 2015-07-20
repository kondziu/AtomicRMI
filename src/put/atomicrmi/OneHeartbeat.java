package put.atomicrmi;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Implementation of a heartbeater thread that is a part of failure
 * detection mechanism. This class runs thread that sends a notification
 * signal to every failure monitor observing this transaction. The set of
 * failure monitors cover the set of remote object accessed by this
 * transaction.
 * 
 * Notification signal is sent periodically, currently every 5s.
 * 
 * @author Konrad Siek, Wojciech Mruczkiewicz
 */
public class OneHeartbeat extends Thread {
	
	/**
	 * Delay between notifications.
	 */
	public static final OneHeartbeat thread = new OneHeartbeat("ARMI Hearbeat");
	
	private static final long ESTIMATED_DELAY = 5000;

//	class Heartbeat implements Runnable {
//
//		/**
//		 * Collection of failure monitors that should be notified about this
//		 * thread liveness.
//		 */
//
//
//		boolean shutdown = false;
//		
//		UUID id;
//
//		public void run() {
//			while (true /*or false*/) {
//				try {
//					synchronized (this) {
//						this.wait(TransactionFailureMonitor.FAILURE_TIMEOUT - ESTIMATED_DELAY);
//						for (ITransactionFailureMonitor monitor : monitors) {
//							try {
//								monitor.heartbeat(id);
//							} catch (RemoteException e) {
//								// Do nothing.
//							}
//						}
//					}
//				} catch (InterruptedException e) {
//					if (shutdown) {
//						shutdown = false;
//						return;
//					}
//				}
//			}
//		}
//
//		/**
//		 * Add new failure monitor that requests notifications.
//		 * 
//		 * @param monitor
//		 *            failure monitor that will be notified.
//		 * @throws RemoteException
//		 *             when exception is thrown during retrieval of monitor's
//		 *             unique identifier.
//		 */
//		public synchronized void addFailureMonitor(ITransactionFailureMonitor monitor) throws RemoteException {
//			UUID id = monitor.getId();
//
//			if (!ids.contains(id)) {
//				ids.add(id);
//				monitors.add(monitor);
//			}
//		}
//	}

	public static synchronized void emergencyStart() throws Exception {
		if (thread.isAlive())
			return;
		
		Field field = OneHeartbeat.class.getField("thread");
		
		field.setAccessible(true);
		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
		
		field.set(null, new OneHeartbeat("ARMI Heartbeat"));
	}
	
	public static synchronized void emergencyStop() {
		if (!thread.isAlive()) {
			return;
		}
		
		thread.interrupt();
		thread.monitors.clear(); // should we clean up inside the map too?
	}

	static {
		thread.setDaemon(true);
		thread.start();
	}

	public OneHeartbeat(String name) {
		super(name);
	}

	private boolean run = true;

	// XXX
	private final Map<Object, Queue<ITransactionFailureMonitor>> monitors = new ConcurrentHashMap<Object, Queue<ITransactionFailureMonitor>>();
	private final Map<Object, Set<Object>> ids = new ConcurrentHashMap<Object, Set<Object>>();

	/**
	 * Controller thread is waiting, as opposed to executing some task.
	 */
	// private boolean waiting = false;

	public interface Task {
		boolean condition(OneHeartbeat controller) throws Exception;

		void run(OneHeartbeat controller) throws Exception;

		// Object category();
	}

	@Override
	public void run() {

		// List<Category> checkedCategories = new LinkedList<Category>();

		while (run) {
			/**
			 * If nobody is doing anything, wait until timeout or until interrupt.
			 */			
			try {
				sleep(TransactionFailureMonitor.FAILURE_TIMEOUT - OneHeartbeat.ESTIMATED_DELAY);
			} catch (InterruptedException e) {
				if (!run)
					break;
			}

			for (Object id : monitors.keySet()) {
				Queue<ITransactionFailureMonitor> queue = monitors.get(id);

				for (ITransactionFailureMonitor monitor : queue) {
					try {
						System.err.println("Pinging " + monitor.getId() + " for transaction "+ id + " in Heartbeat");
						monitor.heartbeat(id);
					} catch (RemoteException e) {
						System.err.println("Ignoring " + e.getLocalizedMessage() + " in Heartbeat");
						e.printStackTrace();
					}
				}
			}

			/**
			 * Aaand... go back to waiting.
			 */
		}
	}
	
	public void register(Object id) {
		System.err.println("Registering hearbeat for " + id);
		this.monitors.put(id, new ConcurrentLinkedQueue<ITransactionFailureMonitor>());
		this.ids.put(id, new ConcurrentSkipListSet<Object>());
	}

	public void addFailureMonitor(Object id, ITransactionFailureMonitor monitor) throws RemoteException {
		System.err.println("Adding hearbeat monitor for " + id + " " + monitor.getId());

		UUID mid = monitor.getId();
		
		if (this.ids.get(id).add(mid)) { // returns true if added, false if already present
			this.monitors.get(id).add(monitor);
		}
	}

	public void remove(Object id) {
		System.err.println("Remove hearbeat for " + id);
		
		Queue<ITransactionFailureMonitor> queue = this.monitors.remove(id);
		queue.clear();
		
		Set<Object> set = this.ids.remove(id);
		set.clear();
	}
}
