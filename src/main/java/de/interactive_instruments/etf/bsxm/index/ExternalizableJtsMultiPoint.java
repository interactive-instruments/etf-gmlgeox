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
package de.interactive_instruments.etf.bsxm.index;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.Point;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class ExternalizableJtsMultiPoint implements ExternalizableJtsGeometry {

    private Point[] points;

    public ExternalizableJtsMultiPoint() {}

    public ExternalizableJtsMultiPoint(final MultiPoint geometry) {
        points = new Point[geometry.getNumGeometries()];
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            points[i] = (Point) geometry.getGeometryN(i);
        }
    }

    @Override
    public Geometry toJtsGeometry(final GeometryFactory factory) {
        return factory.createMultiPoint(points);
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeInt(points.length);
        for (int i = 0; i < points.length; i++) {
            out.writeObject(points[i]);
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        points = new Point[in.readInt()];
        for (int i = 0; i < points.length; i++) {
            points[i] = (Point) in.readObject();
        }
    }
}
