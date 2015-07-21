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

import java.io.Serializable;

/**
 * Interface for object proxy serialization replacement. This is necessary in
 * order to properly serialize and deserialize object proxy. During
 * serialization only object proxy instance without {@link ObjectProxyHandler}
 * is written. During deserialization a {@link ObjectProxyHandler} wrapper is
 * created and invocations of {@link ObjectProxyImpl} methods are monitored.
 * 
 * @author Wojciech Mruczkiewicz
 */
public interface ObjectProxySerializer extends Serializable {

	/**
	 * Write method replacement. Provides class with a special implementation of
	 * serialization for {@link ObjectProxyHandler}.
	 * 
	 * @return an instance of {@link ObjectProxySerializerImpl}.
	 */
	Object writeReplace();
}
