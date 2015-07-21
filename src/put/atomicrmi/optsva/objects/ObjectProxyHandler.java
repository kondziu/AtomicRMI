/*
 * Atomic RMI
 *
 * Copyright 2009-2010 Wojciech Mruczkiewicz <Wojciech.Mruczkiewicz@cs.put.poznan.pl>
 *                     Pawel T. Wojciechowski <Pawel.T.Wojciechowski@cs.put.poznan.pl>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details
 *
 * You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package put.atomicrmi.optsva.objects;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;
import put.atomicrmi.optsva.Access;
import put.atomicrmi.optsva.RollbackForcedException;
import put.atomicrmi.optsva.TransactionException;
import put.atomicrmi.optsva.Access.Mode;
import put.atomicrmi.optsva.objects.ObjectProxy.BufferType;

/**
 * Wrapper used to intercept remote object invocations. This is an
 * implementation of cglib {@link InvocationHandler} interface. It is created
 * during {@link ObjectProxyImpl} creation or transfer.
 * 
 * @author Wojciech Mruczkiewicz
 */
public class ObjectProxyHandler implements InvocationHandler {

	/**
	 * Methods that should not be intercepted.
	 */
	private static Set<Method> neutralMethods = new HashSet<Method>();

	/**
	 * Write replacement method used to properly serialize this class.
	 */
	private static Method writeReplaceMethod;

	/**
	 * Interfaces specific for proxy mechanism.
	 */
	private static Class<?>[] proxyInterfaces = new Class<?>[] { ObjectProxy.class, ObjectProxySerializer.class };

	/**
	 * An instance of {@link ObjectProxyImpl} or remote proxy to this object proxy.
	 */
	private ObjectProxy proxy;

	static {
		try {
			neutralMethods.add(Object.class.getMethod("equals", new Class<?>[] { Object.class }));
			neutralMethods.add(Object.class.getMethod("hashCode", new Class<?>[] {}));
			neutralMethods.add(Object.class.getMethod("toString", new Class<?>[] {}));

			for (Method m : ObjectProxy.class.getMethods())
				neutralMethods.add(m);

			writeReplaceMethod = ObjectProxySerializer.class.getMethod("writeReplace", new Class<?>[] {});
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Wraps given object proxy by this invocation handler implementation.
	 * 
	 * @param proxy
	 *            proxy to be wrapped.
	 * @return wrapped object proxy using this invocation handler.
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	public static Object create(ObjectProxy proxy) throws RemoteException {
		return Enhancer.create(null, getArrayOfRemoteInterfaces(proxy.getWrapped().getClass()), new ObjectProxyHandler(
				proxy));
	}

	/**
	 * Private constructor to prevent instantiation from outside of this class.
	 * 
	 * @param proxy
	 *            object proxy that is wrapped.
	 */
	private ObjectProxyHandler(ObjectProxy proxy) {
		this.proxy = proxy;
	}

	public Object invoke(Object obj, Method method, Object[] args) throws Throwable {
		if (neutralMethods.contains(method)) {
			return method.invoke(proxy, args);
		}

		if (method.getName().equals("finalize")) {
			return method.invoke(obj, args);
		}

		if (writeReplaceMethod.equals(method)) {
			return new ObjectProxySerializerImpl(proxy);
		}

		Mode mode = getAccessMode(method);
		if (!modesAgree(mode, proxy.getMode())) {
			throw new TransactionException("Method access mode was " + mode + " which does not agree with the delared "
					+ proxy.getMode());
		}

		/**
		 * PreSync
		 */
		BufferType bufferred;
		try {
			switch (mode) {
			case READ_ONLY:
				bufferred = proxy.preRead();
				break;
			case WRITE_ONLY:
				bufferred = proxy.preWrite();
				break;
			default:
				throw new RemoteException("Illegal access type: " + mode);
			}
		} catch (RemoteException e) {
			if (e.getCause() instanceof RollbackForcedException) {
				throw e.getCause();
			} else {
				if (e.getCause() instanceof TransactionException) {
					throw e.getCause();
				} else {
					throw e;
				}
			}
		}

		/**
		 * Execute method on object or buffer
		 */
		Object result = null;
		switch (bufferred) {
		case LOG_BUFFER:
			proxy.log(method.getName(), method.getParameterTypes(), args);
			// Note: result == null; Not a huge problem, since this is
			// write-only.
			// TODO return some sort of promise/future
			break;
		case NONE:
			method.setAccessible(true);
			result = method.invoke(proxy.getWrapped(), args);
			break;
		case COPY_BUFFER:
			method.setAccessible(true);
			result = method.invoke(proxy.getBuffer(), args);
		}

		/**
		 * PostSync
		 */
		switch (mode) {
		case READ_ONLY:
			proxy.postRead();
			break;
		case WRITE_ONLY:
			proxy.postWrite();
			break;
		default:
			throw new RemoteException("Illegal access type: " + mode);
		}

		return result;
	}

	/**
	 * Check whether the method access mode agrees with the declared access
	 * mode.
	 * 
	 * @param actual
	 *            the access mode of the method
	 * @param declared
	 *            the access mode declared at transaction start
	 * @return <code>true</code> if the method mode is contained within the
	 *         declared mode and <code>false</code> otherwise
	 */
	private boolean modesAgree(Mode actual, Mode declared) {
		if (declared == Mode.ANY) {
			return true;
		}

		if (actual == declared) {
			return true;
		}

		return false;
	}

	/**
	 * Determine the access mode of a method from annotations.
	 * 
	 * @param method
	 * @return access mode
	 */
	private Mode getAccessMode(Method method) {
		Access access = method.getAnnotation(Access.class);
		if (access == null) {
			return Mode.ANY;
		}
		return access.value();
	}

	/**
	 * Lists every interface that should be included when creating an enhanced
	 * proxy.
	 * 
	 * @param objClass
	 *            remote object class.
	 * @return an array of interfaces that should be included.
	 */
	@SuppressWarnings("rawtypes")
	private static Class[] getArrayOfRemoteInterfaces(Class<? extends Object> objClass) {
		Set<Class> interfaces = getRemoteInterfaces(objClass);

		for (Class c : proxyInterfaces)
			interfaces.add(c);

		return interfaces.toArray(new Class[] {});
	}

	/**
	 * Collects an array of every remote interface that is implemented by a
	 * particular object.
	 * 
	 * @param objClass
	 *            object that interfaces should be listed.
	 * @return set of remote interfaces collected.
	 */
	@SuppressWarnings("rawtypes")
	private static Set<Class> getRemoteInterfaces(Class<? extends Object> objClass) {
		Set<Class> infs = new HashSet<Class>();

		if (objClass.getSuperclass() != null)
			infs.addAll(getRemoteInterfaces((Class<? extends Object>) objClass.getSuperclass()));
		else if (objClass.getCanonicalName().equals("java.rmi.Remote"))
			infs.add(objClass);

		for (Class<? extends Object> c : objClass.getInterfaces()) {
			Set<Class> parents = getRemoteInterfaces(c);
			infs.addAll(parents);
			if (objClass.isInterface() && !parents.isEmpty())
				infs.add(objClass);
		}

		return infs;
	}
}
