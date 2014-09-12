package put.unit.vars;

import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.Access;
import put.atomicrmi.Access.Mode;

public interface Variable extends Remote {

	@Access(Mode.READ_ONLY)
	int read() throws RemoteException;

	@Access(Mode.WRITE_ONLY)
	void write(int v) throws RemoteException;
	
	@Access(Mode.ANY)
	void increment() throws RemoteException;

}
