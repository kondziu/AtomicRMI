package put.unit.instrument;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import put.atomicrmi.Instrumentation;
import put.atomicrmi.StateRecorder;

//import put.atomicrmi.Instrumentation;

public class Instrument {

	private Original object;

	@Before
	public void setup() {
		this.object = new Original();
	}

	@After
	public void teardown() {
		this.object = null;
	}

	@Test
	public void instumentObject() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		StateRecorder recorder = new StateRecorder();
		Instrumentation.transform(Original.class, object, recorder);
	}

	@Test
	public void writeBoolean() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setBoolean", new Class<?>[] { boolean.class });
		method.invoke(newObject, new Object[] { true });
	}

	@Test
	public void readBoolean() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getBoolean", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	@Test
	public void writeByte() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setByte", new Class<?>[] { byte.class });
		method.invoke(newObject, new Object[] { (new Byte("1")).byteValue() });
	}

	@Test
	public void readByte() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getByte", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	@Test
	public void writeChar() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setChar", new Class<?>[] { char.class });
		method.invoke(newObject, new Object[] { 'a' });
	}

	@Test
	public void readChar() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getChar", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	@Test
	public void writeDouble() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setDouble", new Class<?>[] { double.class });
		method.invoke(newObject, new Object[] { 1D });
	}

	@Test
	public void readDouble() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getDouble", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	@Test
	public void writeFloat() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setFloat", new Class<?>[] { float.class });
		method.invoke(newObject, new Object[] { 1F });
	}

	@Test
	public void readFloat() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getFloat", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	@Test
	public void writeInt() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setInt", new Class<?>[] { int.class });
		method.invoke(newObject, new Object[] { 1 });
	}

	@Test
	public void readInt() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getInt", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	@Test
	public void writeLong() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setLong", new Class<?>[] { long.class });
		method.invoke(newObject, new Object[] { 1L });
	}

	@Test
	public void readLong() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getLong", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	@Test
	public void writeShort() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setShort", new Class<?>[] { short.class });
		method.invoke(newObject, new Object[] { (new Short("1")).shortValue() });
	}

	@Test
	public void readShort() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getShort", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	@Test
	public void writeObject() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		StateRecorder recorder = new StateRecorder();
		Object newObject = Instrumentation.transform(Original.class, object, recorder);
		Method method = newObject.getClass().getMethod("setObject", new Class<?>[] { Object.class });
		method.invoke(newObject, new Object[] { "a" });
	}

	@Test
	public void readObject() throws ClassNotFoundException, InstantiationException, IllegalAccessException,
			NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		try {
			StateRecorder recorder = new StateRecorder();
			Object newObject = Instrumentation.transform(Original.class, object, recorder);
			Method method = newObject.getClass().getMethod("getObject", new Class<?>[] {});
			method.invoke(newObject, new Object[] {});
			fail("Should have thrown exception by this point.");
		} catch (InvocationTargetException ite) {
			assertEquals(ite.getCause().getClass(), RuntimeException.class);
		}
	}

	//
	// * Method method = cls.getMethod(&quot;someMethod&quot;, new
	// Class&lt;?&gt;[0]);
	// * method.invoke(instrumentedInstance, new Object[0]);
}
