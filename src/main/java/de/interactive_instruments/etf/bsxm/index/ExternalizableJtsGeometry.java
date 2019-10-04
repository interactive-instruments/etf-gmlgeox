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
package de.interactive_instruments.etf.bsxm.index;

import java.io.Externalizable;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;

/**
 * A factory for creating Externalizable JTS geometry wrappers
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
interface ExternalizableJtsGeometry extends Externalizable {

    static Coordinate[] toCoordinateArray(final double[] coordinates) {
        final Coordinate[] coordArray = new Coordinate[coordinates.length / 3];
        for (int i = 0, j = 0; i < coordArray.length; i++, j += 3) {
            coordArray[i] = new Coordinate(coordinates[j], coordinates[j + 1], coordinates[j + 2]);
        }
        return coordArray;
    }

    static LinearRing toLinearRing(final double[] coordinates, final GeometryFactory factory) {
        return new LinearRing(new CoordinateArraySequence(toCoordinateArray(coordinates)), factory);
    }

    static double[] simplify(final CoordinateSequence coordinateSequence) {
        final Coordinate[] coordArray = coordinateSequence.toCoordinateArray();
        final double[] simplified = new double[coordArray.length * 3];
        for (int i = 0, j = 0; i < simplified.length; i += 3, j++) {
            simplified[i] = coordArray[j].x;
            simplified[i + 1] = coordArray[j].y;
            simplified[i + 2] = coordArray[j].z;
        }
        return simplified;
    }

    Geometry toJtsGeometry(final GeometryFactory factory);

    static ExternalizableJtsGeometry create(final Geometry geometry) {
        if (geometry instanceof LineString) {
            return new ExternalizableJtsLineString((LineString) geometry);
        } else if (geometry instanceof Polygon) {
            return new ExternalizableJtsPolygon((Polygon) geometry);
        } else if (geometry instanceof MultiPolygon) {
            return new ExternalizableJtsMultiPolygon((MultiPolygon) geometry);
        } else if (geometry instanceof Point) {
            return new ExternalizableJtsPoint((Point) geometry);
        } else if (geometry instanceof MultiLineString) {
            return new ExternalizableJtsMultiLineString((MultiLineString) geometry);
        } else if (geometry instanceof MultiPoint) {
            return new ExternalizableJtsMultiPoint((MultiPoint) geometry);
        }

        throw new IllegalStateException("Unknown JTS type for serialization: " + geometry.getGeometryType());
    }
}
