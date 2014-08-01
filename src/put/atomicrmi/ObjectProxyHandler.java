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
package put.atomicrmi;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;

/**
 * Wrapper used to intercept remote object invocations. This is an
 * implementation of cglib {@link InvocationHandler} interface. It is created
 * during {@link ObjectProxy} creation or transfer.
 * 
 * @author Wojciech Mruczkiewicz
 */
class ObjectProxyHandler implements InvocationHandler {

	/**
	 * Methods that should not be intercepted.
	 */
	private static Set<Method> methods = new HashSet<Method>();

	/**
	 * Write replacement method used to properly serialize this class.
	 */
	private static Method writeReplaceMethod;

	/**
	 * Interfaces specific for proxy mechanism.
	 */
	private static Class<?>[] proxyInterfaces = new Class<?>[] { IObjectProxy.class, IObjectProxySerializer.class };

	/**
	 * An instance of {@link ObjectProxy} or remote proxy to this object proxy.
	 */
	private IObjectProxy proxy;

	static {
		try {
			methods.add(Object.class.getMethod("equals", new Class<?>[] { Object.class }));
			methods.add(Object.class.getMethod("hashCode", new Class<?>[] {}));
			methods.add(Object.class.getMethod("toString", new Class<?>[] {}));

			for (Method m : IObjectProxy.class.getMethods())
				methods.add(m);

			writeReplaceMethod = IObjectProxySerializer.class.getMethod("writeReplace", new Class<?>[] {});
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
	static Object create(IObjectProxy proxy) throws RemoteException {
		return Enhancer.create(null, getArrayOfRemoteInterfaces(proxy.getWrapped().getClass()), new ObjectProxyHandler(
				proxy));
	}

	/**
	 * Private constructor to prevent instantiation from outside of this class.
	 * 
	 * @param proxy
	 *            object proxy that is wrapped.
	 */
	private ObjectProxyHandler(IObjectProxy proxy) {
		this.proxy = proxy;
	}

	public Object invoke(Object obj, Method method, Object[] args) throws Throwable {
		if (methods.contains(method))
			return method.invoke(proxy, args);

		if (method.getName().equals("finalize"))
			return method.invoke(obj, args);

		if (writeReplaceMethod.equals(method))
			return new ObjectProxySerializer(proxy);

		try {
			proxy.preSync();
		} catch (RemoteException e) {
			if (e.getCause() instanceof RollbackForcedException)
				throw e.getCause();
			else if (e.getCause() instanceof TransactionException)
				throw e.getCause();
			else
				throw e;
		}

		method.setAccessible(true);
		Object result = method.invoke(proxy.getWrapped(), args);

		proxy.postSync();

		return result;
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
