/**
 * Copyright 2010-2019 interactive instruments GmbH
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
package de.interactive_instruments.etf.bsxm;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.internal.EntryDefault;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;

import org.basex.query.QueryException;
import org.basex.query.value.node.ANode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

/**
 * The GeometryManager builds and maintains spatial indexes and an in-memory cache for JTS geometries that can be used with the GmlGeoX module. The cache is filled during the indexing of the geometries and updated when geometries are accessed using the {@link GmlGeoX#getOrCacheGeometry(ANode)} function.
 *
 * @author Clemens Portele (portele <at> interactive-instruments <dot> de)
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
class GeometryManager implements Externalizable {

    private static final Logger logger = LoggerFactory.getLogger(GeometryManager.class);

    // Max cache entries as number
    public static final String ETF_GEOCACHE_SIZE = "etf.gmlgeox.geocache.size";

    // Record hitcounts and misscounts as boolean
    public static final String ETF_GEOCACHE_REC_STATS = "etf.gmlgeox.geocache.statistics";

    /**
     * Geometry cache, where a key is the ID of a database node that represents a geometry, and the value is the JTS geometry parsed from that node.
     */
    private Cache<String, Geometry> geometryCache = null;

    private Map<String, RTree<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> rtreeByIndexName = new HashMap<>();
    private Map<String, List<com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>>> geomIndexEntriesByIndexName = new HashMap<>();

    private HashMap<DBNodeEntry, Envelope> envelopeByDBNodeEntry = new HashMap<>();
    private int maxSizeOfGeometryCache = 100000;

    public GeometryManager() throws QueryException {
        resetCache(Integer.valueOf(System.getProperty(ETF_GEOCACHE_SIZE, "100000")));
    }

    /**
     * Resets the geometry cache, by replacing the existing cache with a new cache of the given size. That means that all cached geometries will be lost.
     *
     * @param maxSize
     *            new cache size
     * @throws QueryException
     */
    public void resetCache(final int maxSize) throws QueryException {
        try {
            if (logger.isDebugEnabled()
                    || Boolean.valueOf(System.getProperty(ETF_GEOCACHE_REC_STATS, "false"))) {
                geometryCache = Caffeine.newBuilder().recordStats().maximumSize(maxSize).build();
            } else {
                geometryCache = Caffeine.newBuilder().maximumSize(maxSize).build();
            }
            this.maxSizeOfGeometryCache = maxSize;
        } catch (Exception e) {
            throw new QueryException("Cache for geometries could not be initialized: " + e.getMessage());
        }
    }

    /**
     * Get a geometry from the cache
     *
     * @param id
     *            the ID of the database node of the geometry to retrieve
     * @return the parsed geometry of the geometry node, or <code>null</code> if no geometry was found
     * @throws Exception
     */
    public com.vividsolutions.jts.geom.Geometry get(String id) throws Exception {
        Geometry geom = geometryCache.getIfPresent(id);
        return geom;
    }

    /**
     * Returns the number of all read accesses to the cache
     *
     * @return number of read accesses to the cache
     */
    public long getCount() {
        return geometryCache.stats().hitCount();
    }

    /**
     * Returns the number of all failed read accesses to the cache
     *
     * @return number of failed read accesses to the cache
     */
    public long getMissCount() {
        return geometryCache.stats().missCount();
    }

    /**
     * Put a feature geometry in the cache
     *
     * @param id
     *            the ID of the database node that represents the geometry
     * @param geom
     *            the geometry to cache
     */
    public void put(String id, com.vividsolutions.jts.geom.Geometry geom) {
        geometryCache.put(id, geom);
    }

    /**
     * Index a geometry
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param entry
     *            the entry referencing the BaseX node (typically of a feature)
     * @param geometry
     *            the geometry to index
     */
    public void index(
            String indexName, DBNodeEntry entry, com.github.davidmoten.rtree.geometry.Geometry geometry) {

        String key = indexName != null ? indexName : "";

        RTree<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry> rtree;

        if (rtreeByIndexName.containsKey(key)) {
            rtree = rtreeByIndexName.get(key);
        } else {
            rtree = RTree.star().create();
        }

        rtree = rtree.add(entry, geometry);

        rtreeByIndexName.put(key, rtree);
    }

    /**
     * Report current size of the named spatial index
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @return size of the spatial index; can be 0 if no index with given name was found
     */
    public int indexSize(String indexName) {

        String key = indexName != null ? indexName : "";

        if (rtreeByIndexName.containsKey(key)) {
            return rtreeByIndexName.get(key).size();
        } else {
            return 0;
        }
    }

    /**
     * return all entries in the named spatial index
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @return iterator over all entries; can be <code>null</code> if no index with given name was found
     */
    public Iterable<DBNodeEntry> search(String indexName) {

        String key = indexName != null ? indexName : "";

        if (rtreeByIndexName.containsKey(key)) {

            final Observable<com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> results = rtreeByIndexName
                    .get(key).entries();
            return results.map(entry -> entry.value()).toBlocking().toIterable();

        } else {
            return null;
        }
    }

    /**
     * Return all entries in the named spatial index whose bounding box intersects with the given bounding box
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param bbox
     *            the bounding box / rectangle
     * @return iterator over all detected entries; can be <code>null</code> if no index with given name was found
     */
    public Iterable<DBNodeEntry> search(
            String indexName, com.github.davidmoten.rtree.geometry.Rectangle bbox) {

        String key = indexName != null ? indexName : "";

        if (rtreeByIndexName.containsKey(key)) {

            final Observable<com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> results = rtreeByIndexName
                    .get(key).search(bbox);
            return results.map(entry -> entry.value()).toBlocking().toIterable();

        } else {
            return null;
        }
    }

    /**
     * Create the named spatial index by bulk loading, using the STR method. Before the index can be built, entries must be added by calling {@link #prepareSpatialIndex(String, DBNodeEntry, Envelope)}.
     *
     * <p>
     * According to https://github.com/ambling/rtree-benchmark, creating an R*-tree using bulk loading is faster than doing so without bulk loading. Furthermore, according to https://en.wikipedia.org/wiki/R-tree, an STR bulk loaded R*-tree is a "very efficient tree".
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @throws QueryException
     *             If the index has already been built.
     */
    public void buildIndexUsingBulkLoading(String indexName) throws QueryException {

        String key = indexName != null ? indexName : "";

        if (rtreeByIndexName.containsKey(key)) {

            throw new QueryException("Spatial index '" + key + "' has already been built.");

        } else if (geomIndexEntriesByIndexName.containsKey(key)) {

            RTree<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry> rtree = RTree.star()
                    .create(geomIndexEntriesByIndexName.get(key));

            rtreeByIndexName.put(key, rtree);

            geomIndexEntriesByIndexName.remove(key);

        } else {
            /* No entries for that index have been added using prepareSpatialIndex(...) -> ignore */
        }
    }

    /**
     * Removes the named spatial index and all its entries.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     */
    public void removeIndex(String indexName) {

        String key = indexName != null ? indexName : "";

        rtreeByIndexName.remove(key);
    }

    /**
     * @param entry
     * @return <code>true</code> if a mapping to an envelope exists for the given DBNode entry, else <code>false</code>s
     */
    public boolean hasEnvelope(DBNodeEntry entry) {
        return envelopeByDBNodeEntry.containsKey(entry);
    }

    /**
     * @param entry
     * @return The envelope stored for the given entry, or <code>null</code> if the manager does not contain a mapping for the given entry.
     */
    public Envelope getEnvelope(DBNodeEntry entry) {
        return envelopeByDBNodeEntry.get(entry);
    }

    /**
     * Adds a mapping from the given entry to the given envelope to this manager.
     *
     * @param entry
     * @param env
     */
    public void addEnvelope(DBNodeEntry entry, Envelope env) {
        envelopeByDBNodeEntry.put(entry, env);
    }

    /**
     * Prepares spatial indexing by caching an entry for the named spatial index.
     *
     * <p>
     * With an explicit call to {@link #buildIndexUsingBulkLoading(String)}, that index is built.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param nodeEntry
     *            represents the node of the element to be indexed
     * @param geometry
     *            the geometry to use in the index
     * @throws QueryException
     */
    public void prepareSpatialIndex(
            String indexName,
            DBNodeEntry nodeEntry,
            com.github.davidmoten.rtree.geometry.Geometry geometry) {

        String key = indexName != null ? indexName : "";

        List<com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> geomIndexEntries;
        if (geomIndexEntriesByIndexName.containsKey(key)) {
            geomIndexEntries = geomIndexEntriesByIndexName.get(key);
        } else {
            geomIndexEntries = new ArrayList<>();
            geomIndexEntriesByIndexName.put(key, geomIndexEntries);
        }

        geomIndexEntries.add(
                new EntryDefault<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>(
                        nodeEntry, geometry));
    }

    /** @return the maximum size of the geometry cache */
    public int getCacheSize() {
        return this.maxSizeOfGeometryCache;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {

        out.writeInt(maxSizeOfGeometryCache);

        // create serializable representation of geometry cache
        /* Tests revealed that even though JTS Geometry is declared as Serializable, some geometries may contain objects that are not serializable. Accordingly, we encounterd NotSerializableExceptions. That is why JTS geometries are written to WKT strings, which can be serialized. */
        HashMap<String, String> cacheAsHashMap = new HashMap<String, String>();
        WKTWriter wktWriter = new WKTWriter();
        for (Entry<String, Geometry> entry : geometryCache.asMap().entrySet()) {
            String geomNodeId = entry.getKey();
            String geomAsWkt = wktWriter.write(entry.getValue());
            cacheAsHashMap.put(geomNodeId, geomAsWkt);
        }

        out.writeObject(cacheAsHashMap);

        // Serialize geomIndexEntriesByIndexName
        /* serialize the collection of index names first, ensure sorting of index names is available, for controlled serialization of entry lists. */
        TreeSet<String> geomIndexSet = new TreeSet<String>(geomIndexEntriesByIndexName.keySet());
        out.writeObject(geomIndexSet);

        for (String index : geomIndexSet) {

            List<com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> geomIndexEntries = geomIndexEntriesByIndexName
                    .get(index);
            out.writeInt(geomIndexEntries.size());
            serializeGeometryIndexEntries(out, geomIndexEntries);
        }

        // Serialize rtreeByIndexName
        /* NOTE: According to issues on GitHub, flatbuffers based serialization is a size limitation of 2GB. That is why we need another approach that works with Externalizable. */

        /* serialize the collection of index names first, ensure sorting of index names is available, for controlled serialization of rtrees. */
        TreeSet<String> rtreeIndexSet = new TreeSet<String>(rtreeByIndexName.keySet());
        out.writeObject(rtreeIndexSet);

        for (String index : rtreeIndexSet) {

            RTree<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry> rtree = rtreeByIndexName.get(index);
            List<com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> rtreeEntries = new ArrayList<>();
            rtree.entries().subscribe(evt -> rtreeEntries.add(evt));

            out.writeInt(rtreeEntries.size());
            serializeGeometryIndexEntries(out, rtreeEntries);
        }

        // serialize envelopeByDBNodeEntry
        out.writeObject(envelopeByDBNodeEntry);
    }

    private void serializeGeometryIndexEntries(
            ObjectOutput out,
            List<com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> geomIndexEntries)
            throws IOException {

        // now serialize all entries
        for (com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry> entry : geomIndexEntries) {

            DBNodeEntry dbne = entry.value();
            out.writeObject(dbne);

            com.github.davidmoten.rtree.geometry.Geometry geom = entry.geometry();

            if (geom instanceof com.github.davidmoten.rtree.geometry.Point) {

                com.github.davidmoten.rtree.geometry.Point point = (com.github.davidmoten.rtree.geometry.Point) geom;

                out.writeUTF("POINT");
                out.writeFloat(point.x());
                out.writeFloat(point.y());

            } else if (geom instanceof com.github.davidmoten.rtree.geometry.Rectangle) {

                com.github.davidmoten.rtree.geometry.Rectangle rectangle = (com.github.davidmoten.rtree.geometry.Rectangle) geom;

                out.writeUTF("RECTANGLE");
                out.writeFloat(rectangle.x1());
                out.writeFloat(rectangle.x2());
                out.writeFloat(rectangle.y1());
                out.writeFloat(rectangle.y2());

            } else {
                throw new IOException(
                        "Externalization of geometry type "
                                + geom.getClass().getName()
                                + " is not supported yet.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

        this.maxSizeOfGeometryCache = in.readInt();
        try {
            this.resetCache(maxSizeOfGeometryCache);
        } catch (QueryException e) {
            throw new IOException(e);
        }

        HashMap<String, String> cacheAsHashMap = (HashMap<String, String>) in.readObject();
        WKTReader wktReader = new WKTReader();
        for (Entry<String, String> entry : cacheAsHashMap.entrySet()) {
            String geomNodeId = entry.getKey();
            String geomAsWkt = entry.getValue();
            Geometry geometry;
            try {
                geometry = wktReader.read(geomAsWkt);
                this.geometryCache.put(geomNodeId, geometry);
            } catch (ParseException e) {
                throw new IOException("Could not parse JTS geometry from well-known text.", e);
            }
        }

        /* Read the geometry indexes contents. */
        TreeSet<String> geomIndexSet = (TreeSet<String>) in.readObject();
        for (String index : geomIndexSet) {

            int size = in.readInt();

            List<com.github.davidmoten.rtree.Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> entries = new ArrayList<>(
                    size);

            for (int i = 0; i < size; i++) {
                DBNodeEntry dbne = (DBNodeEntry) in.readObject();
                com.github.davidmoten.rtree.geometry.Geometry geometry = readExternalGeometry(in);
                entries.add(
                        new EntryDefault<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>(
                                dbne, geometry));
            }

            this.geomIndexEntriesByIndexName.put(index, entries);
        }

        /* Read the RTree indexes contents. Builds new RTree for each index. */
        TreeSet<String> rtreeIndexSet = (TreeSet<String>) in.readObject();

        for (String index : rtreeIndexSet) {

            int size = in.readInt();

            for (int i = 0; i < size; i++) {
                DBNodeEntry dbne = (DBNodeEntry) in.readObject();
                com.github.davidmoten.rtree.geometry.Geometry geometry = readExternalGeometry(in);
                prepareSpatialIndex(index, dbne, geometry);
            }

            try {
                buildIndexUsingBulkLoading(index);
            } catch (Exception e) {
                throw new IOException(
                        "Exception encountered when building index '"
                                + index
                                + "' using bulk loading while reading externalized object input.",
                        e);
            }
        }

        this.envelopeByDBNodeEntry = (HashMap<DBNodeEntry, Envelope>) in.readObject();
    }

    private com.github.davidmoten.rtree.geometry.Geometry readExternalGeometry(ObjectInput in)
            throws IOException {

        com.github.davidmoten.rtree.geometry.Geometry geometry;

        String geometryType = in.readUTF();
        if (geometryType.equalsIgnoreCase("POINT")) {
            float x = in.readFloat();
            float y = in.readFloat();
            geometry = Geometries.point(x, y);
        } else if (geometryType.equalsIgnoreCase("RECTANGLE")) {
            float x1 = in.readFloat();
            float x2 = in.readFloat();
            float y1 = in.readFloat();
            float y2 = in.readFloat();
            geometry = Geometries.rectangle(x1, y1, x2, y2);
        } else {
            throw new IOException(
                    "Unsupported geometry type encountered while reading externalized object input.");
        }

        return geometry;
    }
}
