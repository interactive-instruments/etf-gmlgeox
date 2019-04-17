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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.internal.EntryDefault;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

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
class GeometryManager {

    private static final Logger logger = LoggerFactory
            .getLogger(GeometryManager.class);

    // Max cache entries as number
    public static final String ETF_GEOCACHE_SIZE = "etf.gmlgeox.geocache.size";

    // Record hitcounts and misscounts as boolean
    public static final String ETF_GEOCACHE_REC_STATS = "etf.gmlgeox.geocache.statistics";

    private final Cache<String, Geometry> geometryCache;
    private Map<String, RTree<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> rtreeByIndexName = new HashMap<>();
    private Map<String, List<Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>>> geomIndexEntriesByIndexName = new HashMap<>();
    private Map<DBNodeEntry, Envelope> envelopeByDBNodeEntry = new HashMap<>();

    GeometryManager() throws QueryException {
        this(Integer.valueOf(System.getProperty(ETF_GEOCACHE_SIZE, "100000")));
    }

    GeometryManager(final int maxSize) throws QueryException {
        try {
            if (logger.isDebugEnabled() || Boolean.valueOf(
                    System.getProperty(ETF_GEOCACHE_REC_STATS, "false"))) {
                geometryCache = Caffeine.newBuilder().recordStats()
                        .maximumSize(maxSize).build();
            } else {
                geometryCache = Caffeine.newBuilder().maximumSize(maxSize)
                        .build();
            }
        } catch (Exception e) {
            throw new QueryException(
                    "Cache for geometries could not be initialized: "
                            + e.getMessage());
        }
    }

    /**
     * Get a geometry from the cache
     *
     * @param e
     *            the entry identifying the database node of the geometry to retrieve
     * @return the parsed geometry of the geometry node, or <code>null</code> if no geometry was found
     * @throws Exception
     */
    public com.vividsolutions.jts.geom.Geometry get(String id)
            throws Exception {
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
     * @param e
     *            a database entry referencing the geometry node
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
    public void index(String indexName, DBNodeEntry entry,
            com.github.davidmoten.rtree.geometry.Geometry geometry) {

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

            final Observable<Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> results = rtreeByIndexName
                    .get(key).entries();
            return results.map(entry -> entry.value()).toBlocking()
                    .toIterable();

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
    public Iterable<DBNodeEntry> search(String indexName,
            com.github.davidmoten.rtree.geometry.Rectangle bbox) {

        String key = indexName != null ? indexName : "";

        if (rtreeByIndexName.containsKey(key)) {

            final Observable<Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> results = rtreeByIndexName
                    .get(key).search(bbox);
            return results.map(entry -> entry.value()).toBlocking()
                    .toIterable();

        } else {
            return null;
        }

    }

    /**
     * Create the named spatial index by bulk loading, using the STR method. Before the index can be built, entries must be added by calling {@link #prepareSpatialIndex(String, DBNodeEntry, Envelope)}.
     * <p>
     * According to https://github.com/ambling/rtree-benchmark, creating an R*-tree using bulk loading is faster than doing so without bulk loading. Furthermore, according to https://en.wikipedia.org/wiki/R-tree, an STR bulk loaded R*-tree is a "very efficient tree".
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @throws QueryException
     *             If the index has already been built.
     */
    public void buildIndexUsingBulkLoading(String indexName)
            throws QueryException {

        String key = indexName != null ? indexName : "";

        if (rtreeByIndexName.containsKey(key)) {

            throw new QueryException(
                    "Spatial index '" + key + "' has already been built.");

        } else if (geomIndexEntriesByIndexName.containsKey(key)) {

            RTree<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry> rtree = RTree
                    .star().create(geomIndexEntriesByIndexName.get(key));

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
     * <p>
     * With an explicit call to {@link #buildIndexUsingBulkLoading(String)}, that index is built.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param nodeEntry
     *            represents the node of the element to be indexed
     * @param geometry
     *            the geometry to use in the index
     *
     * @throws QueryException
     */
    public void prepareSpatialIndex(String indexName, DBNodeEntry nodeEntry,
            com.github.davidmoten.rtree.geometry.Geometry geometry) {

        String key = indexName != null ? indexName : "";

        List<Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> geomIndexEntries;
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
}
