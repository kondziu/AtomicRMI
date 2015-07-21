package put.atomicrmi.optsva.sync;

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
 * This class runs automatically at start. It is also weakly consistent so a new
 * monitor might not be immediately seen when first added or immediately removed
 * (we assume that if a ping or two is missed, the fault detector will not be
 * Triggered.)
 * 
 * The heartbeat does not react if a monitor is inaccessible however.
 * 
 * @author Konrad Siek, Wojciech Mruczkiewicz
 */
public class Heartbeat extends Thread {

	/**
	 * Delay between notifications.
	 */
	private static final long ESTIMATED_DELAY = 5000;

	/**
	 * The default thread.
	 */
	public static final Heartbeat thread = new Heartbeat("ARMI Hearbeat");

	/**
	 * Start default thread.
	 */
	static {
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Restart the thread after an emergency stop.
	 * 
	 * @throws Exception
	 */
	public final static synchronized void emergencyStart() throws Exception {
		if (thread.isAlive())
			return;

		Field field = Heartbeat.class.getField("thread");
		field.setAccessible(true);
		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
		field.set(null, new Heartbeat("ARMI Heartbeat"));
	}

	/**
	 * Stops the thread.
	 */
	public final static synchronized void emergencyStop() {
		if (!thread.isAlive())
			return;

		thread.interrupt();

		// XXX Should we clean up inside the maps too?
		// thread.monitors.clear();
		// thread.ids.clear();
	}

	public Heartbeat(String name) {
		super(name);

		monitors = new ConcurrentHashMap<Object, Queue<TransactionFailureMonitor>>();
		ids = new ConcurrentHashMap<Object, Set<Object>>();
	}

	private boolean run = true;

	/**
	 * Map of monitors, where each monitor belongs to a transaction identified
	 * by the key. A transaction can have several monitors (one per host of any
	 * remote object that it accesses, basically). This is a weakly consistent
	 * concurrent structure.
	 */
	private final Map<Object, Queue<TransactionFailureMonitor>> monitors;

	/**
	 * Map of monitor identifiers per transaction. This is used not to register
	 * multiple equivalent monitors for a single transaction. We explicitly use
	 * monitor identifiers since monitors are RMI remote object stubs and
	 * therefore cannot be used directly for things like comparison.
	 */
	private final Map<Object, Set<Object>> ids;

	@Override
	public void run() {

		while (run) {
			/**
			 * If nobody is doing anything, wait until timeout or until an
			 * interrupt.
			 */
			try {
				sleep(TransactionFailureMonitorImpl.FAILURE_TIMEOUT - Heartbeat.ESTIMATED_DELAY);
			} catch (InterruptedException e) {
				if (!run)
					break;
			}

			/**
			 * Ping everybody on the list.
			 */
			for (Object id : monitors.keySet()) {
				final Queue<TransactionFailureMonitor> queue = monitors.get(id);

				for (TransactionFailureMonitor monitor : queue)
					try {
						monitor.heartbeat(id);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
			}
		}
	}

	/**
	 * Register a transaction by ID whose heartbeat signals will be sent to
	 * monitors specified through {@link Heartbeat.addFailureMonitor}.
	 * 
	 * @param id
	 *            transaction ID
	 */
	public void register(Object id) {
		this.monitors.put(id, new ConcurrentLinkedQueue<TransactionFailureMonitor>());
		this.ids.put(id, new ConcurrentSkipListSet<Object>());
	}

	/**
	 * Register a monitor for a particular transaction. The hearbeat thread will
	 * start sending signals to that monitor henceforth.
	 * 
	 * @param id
	 *            transaction ID
	 * @param monitor
	 *            monitor reference
	 * @throws RemoteException
	 */
	public void addFailureMonitor(Object id, TransactionFailureMonitor monitor) throws RemoteException {
		final UUID mid = monitor.getUID();

		if (this.ids.get(id).add(mid))
			this.monitors.get(id).add(monitor);
	}

	/**
	 * Remove all monitors for a particular transaction.
	 * 
	 * @param id
	 *            transaction ID
	 */
	public void remove(Object id) {
		if (this.monitors.containsKey(id)) {
			final Queue<TransactionFailureMonitor> queue = this.monitors.remove(id);
			queue.clear();
		}

		if (this.ids.containsKey(id)) {
			final Set<Object> set = this.ids.remove(id);
			set.clear();
		}
	}
}
