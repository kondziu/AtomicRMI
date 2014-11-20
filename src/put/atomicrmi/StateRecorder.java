package put.atomicrmi;

import java.lang.reflect.Field;
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
public class StateRecorder implements InterceptFieldCallback {

	/**
	 * Helper class: primitives and universal object types.
	 * 
	 * @author Konrad Siek
	 */
	static private enum FType {
		Int, Float, Byte, Char, Object, Double, Long, Short, Boolean
	}

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
	private Map<String, Pair<FType, Object>> state = new HashMap<String, Pair<FType, Object>>();

	/**
	 * Apply changes registered during the instrumented execution to object.
	 * 
	 * @param object
	 *            original object
	 * @throws NoSuchFieldException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	public void applyChanges(Object object) throws NoSuchFieldException, SecurityException, IllegalArgumentException,
			IllegalAccessException {
		final Class<?> cls = object.getClass();
		for (String fieldName : state.keySet()) {
			
			
			for (Field f : object.getClass().getFields()){
				System.out.println(f.getName());
			}
			
			Field field = cls.getField(fieldName);
			Pair<FType, Object> pair = state.get(fieldName);
			//System.err.println("apply: " + fieldName + "=" + pair.left + "," + pair.right);

			switch (pair.left) {
			case Boolean:
				field.setBoolean(object, ((Boolean) pair.right).booleanValue());
				break;
			case Byte:
				field.setByte(object, ((Byte) pair.right).byteValue());
				break;
			case Char:
				field.setChar(object, ((Character) pair.right).charValue());
				break;
			case Double:
				field.setDouble(object, ((Double) pair.right).doubleValue());
				break;
			case Float:
				field.setFloat(object, ((Float) pair.right).floatValue());
				break;
			case Int:
				field.setInt(object, ((Integer) pair.right).intValue());
				break;
			case Long:
				field.setLong(object, ((Long) pair.right).longValue());
				break;
			case Object:
				field.set(object, pair.right);
				break;
			case Short:
				field.setShort(object, ((Short) pair.right).shortValue());
				break;
			}
		}
	}

	public boolean readBoolean(Object object, String name, boolean value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public byte readByte(Object object, String name, byte value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public char readChar(Object object, String name, char value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public double readDouble(Object object, String name, double value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public float readFloat(Object object, String name, float value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public int readInt(Object object, String name, int value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public long readLong(Object object, String name, long value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public Object readObject(Object object, String name, Object value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public short readShort(Object object, String name, short value) {
		System.err.println("READ " + object + " " + name + " " + value);

		if (!state.containsKey(name)) {
			throw new RuntimeException("Reading a value from a field that was not yet bufferred.");
		}

		return value;
	}

	public boolean writeBoolean(Object object, String name, boolean oldValue, boolean newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Boolean, newValue));

		return newValue;
	}

	public byte writeByte(Object object, String name, byte oldValue, byte newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Byte, newValue));

		return newValue;
	}

	public char writeChar(Object object, String name, char oldValue, char newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Char, newValue));

		return newValue;
	}

	public double writeDouble(Object object, String name, double oldValue, double newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Double, newValue));

		return newValue;
	}

	public float writeFloat(Object object, String name, float oldValue, float newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Float, newValue));

		return newValue;
	}

	public int writeInt(Object object, String name, int oldValue, int newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Int, newValue));

		return newValue;
	}

	public long writeLong(Object object, String name, long oldValue, long newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Long, newValue));

		return newValue;
	}

	public Object writeObject(Object object, String name, Object oldValue, Object newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Object, newValue));

		return newValue;
	}

	public short writeShort(Object object, String name, short oldValue, short newValue) {
		System.err.println("WRITE " + object + " " + name + " " + oldValue + " " + newValue);

		state.put(name, new Pair<FType, Object>(FType.Short, newValue));

		return newValue;
	}
}
