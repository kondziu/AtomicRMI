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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.RemoteException;

/**
 * Serialization replacement for {@link ObjectProxyHandler} class.
 * 
 * @author Wojciech Mruczkiewicz
 */
class ObjectProxySerializerImpl implements Serializable {

	/**
	 * Randomly generated serialization UID.
	 */
	private static final long serialVersionUID = 8509705077469171679L;

	/**
	 * An object proxy that should be serialized. This is an actual remote
	 * object that is sent remotely.
	 */
	private ObjectProxy proxy;

	/**
	 * Creates new serializer that serializes a particular object proxy.
	 * 
	 * @param proxy
	 *            object proxy to serialize.
	 */
	ObjectProxySerializerImpl(ObjectProxy proxy) {
		this.proxy = proxy;
	}

	/**
	 * Write method replacement. Uses default serialization.
	 * 
	 * @param stream
	 *            stream where this object should be written to.
	 * @throws IOException
	 *             when I/O exception occurred.
	 * @throws IllegalAccessException
	 *             when access exception occurred.
	 */
	private void writeObject(ObjectOutputStream stream) throws IOException, IllegalAccessException {
		stream.defaultWriteObject();
	}

	/**
	 * Read method replacement. After deserialization this object is wrapped by
	 * {@link ObjectProxyHandler} and returned to user.
	 * 
	 * @return object proxy wrapped by {@link ObjectProxyHandler}.
	 * @throws RemoteException
	 *             when remote execution failed.
	 */
	private Object readResolve() throws RemoteException {
		return ObjectProxyHandler.create(proxy);
	}
}
