package unit.vars;

import java.rmi.RemoteException;
import java.util.List;

import soa.atomicrmi.TransactionalUnicastRemoteObject;

public class VariableImpl extends TransactionalUnicastRemoteObject implements Variable {

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

	public int read() {
		if (log != null) {
			log.add("r(" + name + ")" + value);
		}
		return value;
	}

	public void write(int value) {
		if (log != null) {
			log.add("w(" + name + ")" + value);
		}
		this.value = value;
	}

	public int read(String id) {
		if (log != null) {
			log.add("r<" + id + ">(" + name + ")" + value);
		}
		return value;
	}

	public void write(String id, int value) {
		if (log != null) {
			log.add("w<" + id + ">(" + name + ")" + value);
		}
		this.value = value;
	}

}
