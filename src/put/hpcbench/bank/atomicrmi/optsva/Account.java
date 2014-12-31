package put.hpcbench.bank.atomicrmi.optsva;

import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.Access;
import put.atomicrmi.Access.Mode;

public interface Account extends Remote {
	@Access(Mode.READ_ONLY)
	int read() throws RemoteException;

	@Access(Mode.WRITE_ONLY)
	void write(int value) throws RemoteException;
	
	@Access(Mode.READ_ONLY)
	String getID() throws RemoteException;
}
