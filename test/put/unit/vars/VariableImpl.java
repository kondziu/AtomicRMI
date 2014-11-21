package put.unit.vars;

import static put.atomicrmi.Access.Mode.ANY;
import static put.atomicrmi.Access.Mode.READ_ONLY;
import static put.atomicrmi.Access.Mode.WRITE_ONLY;

import java.lang.reflect.Field;
import java.rmi.RemoteException;
import java.util.List;

import put.atomicrmi.Access;
import put.atomicrmi.Stateful;
import put.atomicrmi.Stateful.FieldType;
import put.atomicrmi.TransactionalUnicastRemoteObject;

public class VariableImpl extends TransactionalUnicastRemoteObject implements Variable, Cloneable, Stateful {

	private static final long serialVersionUID = 8037219139497925795L;
	private int value;
	private String name;
	private List<String> log = null;

	public VariableImpl() throws RemoteException {

	}

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
		// if (log != null) {
		// log.add("r(" + name + ")" + value);
		// }
		return value;
	}

	@Access(value = WRITE_ONLY)
	public void write(int value) {
		// if (log != null) {
		// log.add("w(" + name + ")" + value);
		// }
		this.value = value;
	}

	@Access(value = READ_ONLY)
	public int read(String id) {
		// if (log != null) {
		// log.add("r<" + id + ">(" + name + ")" + value);
		// }
		return value;
	}

	@Access(value = WRITE_ONLY)
	public void write(String id, int value) {
		// if (log != null) {
		// log.add("w<" + id + ">(" + name + ")" + value);
		// }
		this.value = value;
	}

	@Access(value = ANY)
	public void increment() throws RemoteException {
		write(read() + 1);
	}

	public void set(String fieldName, FieldType type, Object value) throws RemoteException {
		try {
			System.err.println(getClass());
			for(Field f : getClass().getDeclaredFields()) {
				System.err.println("---> " + f.getName());
			}
			System.err.println();
			Field field = this.getClass().getDeclaredField(fieldName);
			switch (type) {
			case Boolean:
				field.setBoolean(this, ((Boolean) value).booleanValue());
				break;
			case Byte:
				field.setByte(this, ((Byte) value).byteValue());
				break;
			case Char:
				field.setChar(this, ((Character) value).charValue());
				break;
			case Double:
				field.setDouble(this, ((Double) value).doubleValue());
				break;
			case Float:
				field.setFloat(this, ((Float) value).floatValue());
				break;
			case Int:
				field.setInt(this, ((Integer) value).intValue());
				break;
			case Long:
				field.setLong(this, ((Long) value).longValue());
				break;
			case Object:
				field.set(this, value);
				break;
			case Short:
				field.setShort(this, ((Short) value).shortValue());
				break;
			}
		} catch (Exception e) {
			throw new RemoteException(e.getLocalizedMessage(), e.getCause());
		}
	}
}
