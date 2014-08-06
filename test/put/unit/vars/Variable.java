package put.unit.vars;

import java.rmi.Remote;
import java.rmi.RemoteException;

import put.atomicrmi.Access;
import put.atomicrmi.Access.Mode;

public interface Variable extends Remote {

	@Access(mode=Mode.READ_ONLY)
	int read() throws RemoteException;

	@Access(mode=Mode.WRITE_ONLY)
	void write(int v) throws RemoteException;
	
	@Access(mode=Mode.ANY)
	void increment() throws RemoteException;

}
