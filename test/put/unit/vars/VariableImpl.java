package put.unit.vars;

import static put.atomicrmi.Access.Mode.ANY;
import static put.atomicrmi.Access.Mode.READ_ONLY;
import static put.atomicrmi.Access.Mode.WRITE_ONLY;

import java.rmi.RemoteException;
import java.util.List;

import put.atomicrmi.Access;
import put.atomicrmi.TransactionalUnicastRemoteObject;

public class VariableImpl extends TransactionalUnicastRemoteObject implements Variable, Cloneable {

	private static final long serialVersionUID = 8037219139497925795L;
	private int value;
	private String name;
	private List<String> log = null;

	public VariableImpl(String name, int value, List<String> log) throws RemoteException {
		super();
		this.name = name;
		this.value = value;
		this.log = log;
	}

	public VariableImpl(String name, int value) throws RemoteException {
		super();
		this.name = name;
		this.value = value;
	}

	@Access(value = READ_ONLY)
	public int read() {
		if (log != null) {
			log.add("r(" + name + ")" + value);
		}
		return value;
	}

	@Access(value = WRITE_ONLY)
	public void write(int value) {
		if (log != null) {
			log.add("w(" + name + ")" + value);
		}
		this.value = value;
	}

	@Access(value = READ_ONLY)
	public int read(String id) {
		if (log != null) {
			log.add("r<" + id + ">(" + name + ")" + value);
		}
		return value;
	}

	@Access(value = WRITE_ONLY)
	public void write(String id, int value) {
		if (log != null) {
			log.add("w<" + id + ">(" + name + ")" + value);
		}
		this.value = value;
	}

	@Access(value = ANY)
	public void increment() throws RemoteException {
		write(read() + 1);
	}
}
