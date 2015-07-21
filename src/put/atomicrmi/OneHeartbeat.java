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
 * Implementation of a heartbeat thread that is a part of failure detection
 * mechanism. This class is a thread that sends a signal to a fault detection
 * server on behalf of all transactions running on the same JVM per remote
 * object in accesses. This is done repeatedly until the transaction is
 * completed. If this signal is not received on the server-side within the
 * specified time, the server assumes a failure of the client.
 * 
 * @author Konrad Siek, Wojciech Mruczkiewicz
 */
public class OneHeartbeat extends Thread {

	/**
	 * Delay between notifications.
	 */
	private static final long ESTIMATED_DELAY = 5000;
	
	public static final OneHeartbeat thread = new OneHeartbeat("ARMI Hearbeat");

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

		// XXX Should we clean up inside the maps too?
		// thread.monitors.clear();
		// thread.ids.clear();
	}

	static {
		thread.setDaemon(true);
		thread.start();
	}

	public OneHeartbeat(String name) {
		super(name);
	}

	private boolean run = true;

	private final Map<Object, Queue<ITransactionFailureMonitor>> monitors = new ConcurrentHashMap<Object, Queue<ITransactionFailureMonitor>>();
	private final Map<Object, Set<Object>> ids = new ConcurrentHashMap<Object, Set<Object>>();

	/**
	 * Controller thread is waiting, as opposed to executing some task.
	 */

	@Override
	public void run() {

		while (run) {
			/**
			 * If nobody is doing anything, wait until timeout or until
			 * interrupt.
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
//						System.err.println("Pinging " + monitor.getId() + " for transaction " + id + " in Heartbeat");
						monitor.heartbeat(id);
					} catch (RemoteException e) {
//						System.err.println("Ignoring " + e.getLocalizedMessage() + " in Heartbeat");
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
		// System.err.println("Registering hearbeat for " + id);
		this.monitors.put(id, new ConcurrentLinkedQueue<ITransactionFailureMonitor>());
		this.ids.put(id, new ConcurrentSkipListSet<Object>());
	}

	public void addFailureMonitor(Object id, ITransactionFailureMonitor monitor) throws RemoteException {
		// System.err.println("Adding hearbeat monitor for " + id + " " +
		// monitor.getId());

		UUID mid = monitor.getId();

		if (this.ids.get(id).add(mid)) { // returns true if added, false if
											// already present
											// System.err.println("Actually adding hearbeat monitor for "
											// + id + " " + monitor.getId());
			this.monitors.get(id).add(monitor);
		}
		// else {
		// System.err.println("Not actually adding hearbeat monitor for " + id +
		// " " + monitor.getId());
		// }
	}

	public void remove(Object id) {
//		System.err.println("Remove hearbeat for " + id);

		if (this.monitors.containsKey(id)) {
			// System.err.println("Actually remove hearbeat for " + id +
			// " monitors");
			Queue<ITransactionFailureMonitor> queue = this.monitors.remove(id);
			queue.clear();
		}

		if (this.ids.containsKey(id)) {
			// System.err.println("Actually remove hearbeat for " + id +
			// " ids");
			Set<Object> set = this.ids.remove(id);
			set.clear();
		}
	}
}
