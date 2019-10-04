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

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public enum SpatialRelOp {
    CONTAINS {
        @Override
        public boolean call(final com.vividsolutions.jts.geom.Geometry g1,
                final com.vividsolutions.jts.geom.Geometry g2) {
            return g1.contains(g2);
        }
    },
    CROSSES {
        @Override
        public boolean call(final com.vividsolutions.jts.geom.Geometry g1,
                final com.vividsolutions.jts.geom.Geometry g2) {
            return g1.crosses(g2);
        }
    },
    EQUALS {
        @Override
        public boolean call(final com.vividsolutions.jts.geom.Geometry g1,
                final com.vividsolutions.jts.geom.Geometry g2) {
            return g1.equals(g2);
        }
    },
    INTERSECTS {
        @Override
        public boolean call(final com.vividsolutions.jts.geom.Geometry g1,
                final com.vividsolutions.jts.geom.Geometry g2) {
            return g1.intersects(g2);
        }
    },
    ISDISJOINT {
        @Override
        public boolean call(final com.vividsolutions.jts.geom.Geometry g1,
                final com.vividsolutions.jts.geom.Geometry g2) {
            return g1.disjoint(g2);
        }
    },
    ISWITHIN {
        @Override
        public boolean call(final com.vividsolutions.jts.geom.Geometry g1,
                final com.vividsolutions.jts.geom.Geometry g2) {
            return g1.within(g2);
        }
    },
    OVERLAPS {
        @Override
        public boolean call(final com.vividsolutions.jts.geom.Geometry g1,
                final com.vividsolutions.jts.geom.Geometry g2) {
            return g1.overlaps(g2);
        }
    },
    TOUCHES {
        @Override
        public boolean call(final com.vividsolutions.jts.geom.Geometry g1,
                final com.vividsolutions.jts.geom.Geometry g2) {
            return g1.touches(g2);
        }
    };
    public abstract boolean call(final com.vividsolutions.jts.geom.Geometry g1, final com.vividsolutions.jts.geom.Geometry g2);
}
