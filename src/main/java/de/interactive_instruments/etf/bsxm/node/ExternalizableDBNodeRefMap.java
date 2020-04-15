/**
 * Copyright 2010-2020 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.bsxm.node;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A class for externalization, in which DBNodeRefs from different sources are collected.
 *
 * If there are several sources that share an object instance, the object instance is created only once during the
 * recovery.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
final public class ExternalizableDBNodeRefMap implements Externalizable {

    private List<DBNodeRef> preparedNodeRefs;
    // map a DBNodeRef to a position in the serialized compressedData
    private Map<DBNodeRef, Integer> nodeRefPositions;

    @Contract(pure = true)
    public ExternalizableDBNodeRefMap() {}

    /**
     * Add a collection of DBNodeRefs and get their positions in this data structure
     *
     * The client must serialize the positions and use the {@link #getByRefPositions(int[]) } method on deserialization.
     *
     * @param collection
     *            a collection of DBNodeRefs
     * @return positions in this data structure for later lookup
     */
    @NotNull
    public int[] addAndGetRefPositions(@NotNull final Collection<DBNodeRef> collection) {
        final int[] positions = new int[collection.size()];
        if (nodeRefPositions == null) {
            preparedNodeRefs = new ArrayList<>(collection);
            nodeRefPositions = new LinkedHashMap<>((int) Math.min((long) collection.size() * 2, (long) Integer.MAX_VALUE - 5));
            for (int i = 0; i < preparedNodeRefs.size(); i++) {
                nodeRefPositions.put(preparedNodeRefs.get(i), i);
            }
            // use a second loop to support loop unwinding
            for (int p = 0; p < positions.length; p++) {
                positions[p] = p;
            }
        } else {
            int pIndex = 0;
            for (final DBNodeRef dbNodeRef : collection) {
                final Integer pos = nodeRefPositions.get(dbNodeRef);
                if (pos == null) {
                    // collect new Object instance
                    nodeRefPositions.put(dbNodeRef, preparedNodeRefs.size());
                    positions[pIndex++] = preparedNodeRefs.size();
                    preparedNodeRefs.add(dbNodeRef);
                } else {
                    // return reference to existing Object instance
                    positions[pIndex++] = pos;
                }
            }
        }
        return positions;
    }

    /**
     * Get an array of DBNodeRefs from an array of positions
     *
     * @param refPositions
     *            array of DBNodeRef positions in this data structure
     * @return an array of DBNodeRefs
     */
    @NotNull
    public DBNodeRef[] getByRefPositions(final int[] refPositions) {
        if (preparedNodeRefs == null) {
            throw new IllegalStateException("ExternalizableDBNodeRefMap not correctly deserialized");
        }
        final DBNodeRef[] dbNodeRefs = new DBNodeRef[refPositions.length];
        for (int i = 0; i < refPositions.length; i++) {
            dbNodeRefs[i] = preparedNodeRefs.get(refPositions[i]);
        }
        return dbNodeRefs;
    }

    @Override
    public void writeExternal(@NotNull final ObjectOutput out) throws IOException {
        final int size = preparedNodeRefs.size();
        out.writeInt(size);
        for (int i = 0; i < size; i++) {
            out.writeLong(preparedNodeRefs.get(i).getNativeData());
        }
    }

    @Override
    public void readExternal(@NotNull final ObjectInput in) throws IOException {
        final int size = in.readInt();
        this.preparedNodeRefs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.preparedNodeRefs.add(DBNodeRef.create(in.readLong()));
        }
    }
}
