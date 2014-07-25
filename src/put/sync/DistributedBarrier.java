package put.sync;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * @author Konrad Siek
 */
public class DistributedBarrier extends UnicastRemoteObject implements Barrier {

	private static final long serialVersionUID = -4159054108828280879L;
	final private CyclicBarrier barrier;

	public DistributedBarrier(int parties) throws RemoteException, IllegalArgumentException {
	    super();
		barrier = new CyclicBarrier(parties);
	}

	public int enter() throws InterruptedException, BrokenBarrierException,
			RemoteException {
		return barrier.await();
	}
}