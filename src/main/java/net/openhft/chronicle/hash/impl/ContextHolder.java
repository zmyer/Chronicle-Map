/*
 *      Copyright (C) 2016 Roman Leventov
 *
 *      This program is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU Lesser General Public License as published by
 *      the Free Software Foundation, either version 3 of the License.
 *
 *      This program is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU Lesser General Public License for more details.
 *
 *      You should have received a copy of the GNU Lesser General Public License
 *      along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.hash.impl;

import net.openhft.chronicle.hash.impl.stage.hash.ChainingInterface;

/**
 * A simple wrapper of {@link ChainingInterface}, the ChainingInterface field could be set to null.
 * <p>
 * <h3>Motivation</h3>
 * <p>{@link net.openhft.chronicle.map.ChronicleMap}'s context objects are huge and reference their
 * own instances of key and value marshallers, which usually have buffers for serialization (e. g.
 * see {@link net.openhft.chronicle.hash.serialization.impl.SerializableDataAccess}). The contexts
 * are stored in {@link ThreadLocal}s, which are <i>instance</i> fields of ChronicleMap objects
 * (see {@link net.openhft.chronicle.map.VanillaChronicleMap#cxt}). We want the context objects to
 * be eligible for garbage collection as soon as possible after the ChronicleMap object is closed
 * or becomes unreachable.
 * <p>
 * <p>In JDK 8 ThreadLocals are implemented using {@link java.lang.ThreadLocal.ThreadLocalMap},
 * a hash table with ThreadLocal objects themselves as the keys, weak-referenced. So after
 * ChronicleMap (hence it's ThreadLocal cxt field) becomes unreachable, context objects should be
 * eventually removed from ThreadLocalMap and become unreachable, but the current implementation
 * does some cleanup of ThreadLocalMap lazily and only on ThreadLocal.set(), initialValue() and
 * remove(), but not on the hot path of ThreadLocal.get(). I. e. if there is not enough "ThreadLocal
 * activity" within a thread, stale ChronicleMap contexts may not be removed from ThreadLocalMaps
 * forever, effectively this is a memory leak.
 * <p>
 * <p>Moreover, if the user of the library closes ChronicleMap with close(), but has ChronicleMap
 * object leaked, ChronicleMap's ThreadLocal field doesn't become unreachable and the leak of
 * context objects is "legitimate".
 * <p>
 * <p>Solution for this is to reference from {@link
 * net.openhft.chronicle.map.VanillaChronicleMap#cxt} not huge context object directly, but small
 * ContextHolder object, and clear the reference to context via {@link #clear()} on
 * ChronicleMap.close() or from {@link sun.misc.Cleaner}'s registered cleaner for ChronicleMap, if
 * it weren't closed, but becomes unreachable and reclaimed by the garbage collector.
 *
 * @see ChronicleHashResources#closeContext(ContextHolder)
 */
public final class ContextHolder {

    private ChainingInterface context;

    public ContextHolder(ChainingInterface context) {
        this.context = context;
    }

    public ChainingInterface get() {
        return context;
    }

    void clear() {
        context = null;
    }
}
