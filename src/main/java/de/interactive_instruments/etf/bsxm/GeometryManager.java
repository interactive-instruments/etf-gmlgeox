/**
 * Copyright 2010-2016 interactive instruments GmbH
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

import java.util.Properties;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.vividsolutions.jts.geom.Geometry;

import org.apache.commons.jcs.JCS;
import org.apache.commons.jcs.access.CacheAccess;
import org.apache.commons.jcs.engine.control.CompositeCacheManager;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.basex.query.QueryException;

import rx.Observable;

/**
 * The GeometryManager is a spatial index and an in-memory cache for JTS geometries that can be used
 * with the GmlGeoX module. The cache is filled during the indexing of the geometries and updated
 * when geometries are accessed using the {@link GmlGeoX#getGeometry(Object, Object)} function.
 *
 *  @author Clemens Portele (portele <at> interactive-instruments <dot> de)
 */
class GeometryManager {

	private int getCount = 0;
	private int missCount = 0;
	private int size = 100000;
	private CacheAccess<String, Geometry> geometryCache;
	private RTree<IndexEntry, com.github.davidmoten.rtree.geometry.Geometry> rtree;

	GeometryManager() throws QueryException {
		try {
			// Just use a default cache region - in memory
			Properties props = new Properties();
			props.put("jcs.default", "");
			props.put("jcs.default.cacheattributes", "org.apache.commons.jcs.engine.CompositeCacheAttributes");
			props.put("jcs.default.cacheattributes.MaxObjects", Integer.toString(size));
			props.put("jcs.default.cacheattributes.MemoryCacheName", "org.apache.commons.jcs.engine.memory.lru.LRUMemoryCache");

			CompositeCacheManager ccm = CompositeCacheManager.getUnconfiguredInstance();
			ccm.configure(props);

			// If log4j level is set to debug, jcs will execute the method
			// AbstractDoubleLinkedListMemoryCache.verifyCache() and may hang in an infinite loop
			// due to a possible cycle in a double linked list.
			// Might be a good idea to set the root level to ERROR, so that other blocks which
			// check the log level with isDebugEnabled() method are not executed
			// Logger.getLogger(AbstractDoubleLinkedListMemoryCache.class).setLevel(Level.ERROR);
			// Todo: use log4j to slf4j bridge if log4j is not required anymore in ii-commons-util
			Logger.getRootLogger().setLevel(Level.ERROR);

			geometryCache = JCS.getInstance("geometryCache");
			rtree = RTree.star().create();
		} catch (Exception e) {
			throw new QueryException("Cache for geometries could not be initialized.");
		}
	}

	void setSize(int s) throws QueryException {
		size = s;
	}

	/**
	 * Get feature geometry from the cache
	 *
	 * @param id
	 *            the id for which the geometry should be retrieved, typically a gml:id of a GML feature element
	 * @return
	 *            the geometry of the indexed node, or null if no geometry was found
	 */
	public com.vividsolutions.jts.geom.Geometry get(String id) {
		com.vividsolutions.jts.geom.Geometry geom = geometryCache.get(id);
		getCount++;
		if (geom == null)
			missCount++;
		return geom;
	}

	/**
	 * Returns the number of all read accesses to the cache
	 *
	 * @return number of read accesses to the cache
	 */
	public int getCount() {
		return getCount;
	}

	/**
	 * Returns the number of all failed read accesses to the cache
	 *
	 * @return number of failed read accesses to the cache
	 */
	public int getMissCount() {
		return missCount;
	}

	/**
	 * Put a feature geometry in the cache
	 *
	 * @param id
	 *            an id of the geometry, typically a gml:id of a GML feature element
	 * @param geom
	 *            the geometry to cache
	 */
	public void put(String id, com.vividsolutions.jts.geom.Geometry geom) {
		geometryCache.put(id, geom);
	}

	/**
	 * Index a geometry
	 *
	 * @param entry the index entry referencing the BaseX node
	 * @param geometry the geometry to index
	 */
	public void index(IndexEntry entry, com.github.davidmoten.rtree.geometry.Geometry geometry) {
		rtree = rtree.add(entry, geometry);
	}

	/**
	 * Report current size of the spatial index
	 *
	 * @return  size of the spatial index
	 */
	public int indexSize() {
		return rtree.size();
	}

	/**
	 * return all entries in the spatial index
	 *
	 * @return  iterator over all entries
	 */
	public Iterable<IndexEntry> search() {
		Observable<Entry<IndexEntry, com.github.davidmoten.rtree.geometry.Geometry>> results = rtree.entries();
		return results.map(entry -> entry.value()).toBlocking().toIterable();
	}

	/**
	 * return all entries in the spatial index that are in the bounding box
	 *
	 * @param bbox  the bounding box / rectangle
	 * @return  iterator over all detected entries
	 */
	public Iterable<IndexEntry> search(com.github.davidmoten.rtree.geometry.Rectangle bbox) {
		Observable<Entry<IndexEntry, com.github.davidmoten.rtree.geometry.Geometry>> results = rtree.search(bbox);
		return results.map(entry -> entry.value()).toBlocking().toIterable();
	}
}
