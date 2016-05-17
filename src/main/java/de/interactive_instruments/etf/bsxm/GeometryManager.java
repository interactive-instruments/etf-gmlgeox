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

import com.vividsolutions.jts.geom.Geometry;
import org.apache.commons.jcs.JCS;
import org.apache.commons.jcs.access.CacheAccess;
import org.apache.commons.jcs.engine.control.CompositeCacheManager;
import org.basex.query.QueryException;

import java.util.Properties;

/**
 * The GeometryManager is an in-memory cache for JTS geometries that can be used
 * with the GmlGeoX module. The cache is filled during the indexing of the geometries
 * and updated when geometries are accessed using the {@link GmlGeoX#getGeometry(Object, Object)} function.
 *
 *  @author Clemens Portele (portele <at> interactive-instruments <dot> de)
 */
class GeometryManager {

    private static GeometryManager instance;
    private static int checkedOut = 0;
    private static int getCount = 0;
    private static int missCount = 0;
    private static int size = 100000;
    private static CacheAccess<String, Geometry> geometryCache;

        private GeometryManager() throws QueryException {
            try
            {
                // Just use a default cache region - in memory
                Properties props = new Properties();
                props.put("jcs.default","");
                props.put("jcs.default.cacheattributes","org.apache.commons.jcs.engine.CompositeCacheAttributes");
                props.put("jcs.default.cacheattributes.MaxObjects", Integer.toString(size));
                props.put("jcs.default.cacheattributes.MemoryCacheName","org.apache.commons.jcs.engine.memory.lru.LRUMemoryCache");

                CompositeCacheManager ccm = CompositeCacheManager.getUnconfiguredInstance();
                ccm.configure(props);

                geometryCache = JCS.getInstance("geometryCache");
            }
            catch (Exception e)
            {
                throw new QueryException("Cache for geometries could not be initialized.");
            }
        }

        /**
         * Singleton access point to the manager.
         */
        public static GeometryManager getInstance() throws QueryException {
            synchronized (GeometryManager.class)
            {
                if (instance == null)
                {
                    instance = new GeometryManager();
                }
            }

            synchronized (instance)
            {
                instance.checkedOut++;
            }

            return instance;
        }

    static void setSize(int s) throws QueryException {
        synchronized (GeometryManager.class)
        {
            if (instance == null)
            {
                size = s;
            }
        }
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
        synchronized (geometryCache)
        {
            com.vividsolutions.jts.geom.Geometry geom = geometryCache.get(id);
            getCount++;
            if (geom==null && ++missCount%10000==0)
                System.out.println("GmlGeoX#getGeometry cache misses: "+missCount+" of "+getCount+" ");

            return geom;
        }
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
        synchronized (geometryCache) {
            geometryCache.put(id, geom);
        }
    }

}
