package put.atomicrmi;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

import net.sf.cglib.transform.impl.InterceptFieldCallback;

/**
 * Instrumentation for intercepting field accesses in write buffers.
 * 
 * <p>
 * The purpose of this code is to be injected into an object by CGlib and record
 * changed values of fields as they are being accessed.
 */
public class FieldStateRecorder implements StateRecorder, InterceptFieldCallback {

	/**
	 * Helper class: pair.
	 * 
	 * @author Konrad Siek
	 * 
	 * @param <T1>
	 *            left field type
	 * @param <T2>
	 *            right field type
	 */
	private class Pair<T1, T2> {
		public Pair(T1 left, T2 right) {
			this.left = left;
			this.right = right;
		}

		T1 left;
		T2 right;
	}

	/**
	 * The state recorded by the instrumented accesses.
	 */
	private Map<String, Pair<Stateful.FieldType, Object>> state = new HashMap<String, Pair<Stateful.FieldType, Object>>();

	/**
	 * Apply changes registered during the instrumented execution to object.
	 * 
	 * @param object
	 *            original object
	 * @throws NoSuchFieldException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws RemoteException 
	 */
	public void applyChanges(Object object) throws NoSuchFieldException, SecurityException, IllegalArgumentException,
			IllegalAccessException, RemoteException {
		//final Class<?> cls = object.getClass();
		for (String fieldName : state.keySet()) {
			
			Pair<Stateful.FieldType, Object> pair = state.get(fieldName);
			//System.err.println("apply: " + fieldName + "=" + pair.left + "," + pair.right);
			
			((Stateful) object).set(fieldName, pair.left, pair.right);	
		}
	}

	public boolean readBoolean(Object object, String name, boolean value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public byte readByte(Object object, String name, byte value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public char readChar(Object object, String name, char value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public double readDouble(Object object, String name, double value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public float readFloat(Object object, String name, float value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public int readInt(Object object, String name, int value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public long readLong(Object object, String name, long value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public Object readObject(Object object, String name, Object value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public short readShort(Object object, String name, short value) {
		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public boolean writeBoolean(Object object, String name, boolean oldValue, boolean newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Boolean, newValue));

		return newValue;
	}

	public byte writeByte(Object object, String name, byte oldValue, byte newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Byte, newValue));

		return newValue;
	}

	public char writeChar(Object object, String name, char oldValue, char newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Char, newValue));

		return newValue;
	}

	public double writeDouble(Object object, String name, double oldValue, double newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Double, newValue));

		return newValue;
	}

	public float writeFloat(Object object, String name, float oldValue, float newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Float, newValue));

		return newValue;
	}

	public int writeInt(Object object, String name, int oldValue, int newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Int, newValue));

		return newValue;
	}

	public long writeLong(Object object, String name, long oldValue, long newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Long, newValue));

		return newValue;
	}

	public Object writeObject(Object object, String name, Object oldValue, Object newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Object, newValue));

		return newValue;
	}

	public short writeShort(Object object, String name, short oldValue, short newValue) {
		state.put(name, new Pair<Stateful.FieldType, Object>(Stateful.FieldType.Short, newValue));

		return newValue;
	}
}
