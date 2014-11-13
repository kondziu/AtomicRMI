package put.atomicrmi;

import net.sf.cglib.asm.Type;
import net.sf.cglib.transform.ClassFilter;
import net.sf.cglib.transform.ClassTransformer;
import net.sf.cglib.transform.ClassTransformerFactory;
import net.sf.cglib.transform.TransformingClassLoader;
import net.sf.cglib.transform.impl.InterceptFieldCallback;
import net.sf.cglib.transform.impl.InterceptFieldEnabled;
import net.sf.cglib.transform.impl.InterceptFieldFilter;
import net.sf.cglib.transform.impl.InterceptFieldTransformer;

/**
 * Instrumentation toolkit.
 * 
 * <p>
 * Uses CGlib to intercept field accesses. The purpose
 * of the majority of the code is to get CGlib to do my bidding.
 * 
 * @author Konrad Siek
 */
public class Instrumentation {

	/**
	 * Specification of the fields that are to be instrumented. Here, all of
	 * them.
	 */
	static private final InterceptFieldTransformer transformer = new InterceptFieldTransformer(
			new InterceptFieldFilter() {
				public boolean acceptWrite(Type type, String name) {
					return true;
				}

				public boolean acceptRead(Type type, String name) {
					return true;
				}
			});

	/**
	 * A pointless factory class for the transformer required by CGlib.
	 */
	static private final ClassTransformerFactory transformerFactory = new ClassTransformerFactory() {
		public ClassTransformer newInstance() {
			return transformer;
		}
	};

	/**
	 * Instrument an object to intercept field accesses.
	 * 
	 * <p>
	 * Once intercepted, the field accesses will perform operations specified
	 * within the <code>read*</code> and <code>write*</code> methods in the
	 * callback object.
	 * 
	 * <b>Warning</b> The resulting instrumented object <b>cannot be</b> cast to
	 * its original class. Instead, its methods need to be called by reflection.
	 * E.g.,
	 * 
	 * <pre>
	 * Object instrumentedInstance = Instrumentation.transform(cls, instance);
	 * Method method = instrumentedInstance.getClass().getMethod(&quot;someMethod&quot;, new Class&lt;?&gt;[0]);
	 * method.invoke(instrumentedInstance, new Object[0]);
	 * </pre>
	 * 
	 * @param cls
	 *            instrumented object class
	 * @param object
	 *            the original object to be instrumented
	 * @param callback
	 *            the class that does the actual instrumentation
	 * @return an instrumented instance of the original object (see warning
	 *         above)
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	static public final <T> Object transform(final Class<?> cls, final T object, InterceptFieldCallback callback)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		final ClassLoader loader = new TransformingClassLoader(Instrumentation.class.getClassLoader(),
				new ClassFilter() {
					public boolean accept(String name) {
						return name.equals(cls.getName());
					}
				}, transformerFactory);

		Class<?> newClass = loader.loadClass(cls.getName());
		Object instance = newClass.newInstance();

		InterceptFieldEnabled instanceCast = (InterceptFieldEnabled) instance;
		instanceCast.setInterceptFieldCallback(callback);

		return instance;
	}

}
