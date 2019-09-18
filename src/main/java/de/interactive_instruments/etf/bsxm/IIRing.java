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
import java.util.LinkedList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;

import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.precision.PrecisionModel;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.segments.CurveSegment;
import org.deegree.geometry.primitive.segments.LineStringSegment;
import org.deegree.geometry.standard.primitive.DefaultCurve;
import org.deegree.geometry.standard.primitive.DefaultRing;

/**
 * Based on the implementation of {@link DefaultRing}.
 */
public class IIRing extends DefaultRing {

    protected IIGeometryFactory geomFactory = null;
    protected CustomCurveLinearizer linearizer = null;

    /**
     * Creates a new {@link DefaultCurve} instance from the given parameters.
     *
     * @param id
     *            identifier, may be null
     * @param crs
     *            coordinate reference system, may be null
     * @param pm
     *            precision model, may be null
     * @param members
     *            the <code>Curve</code>s that compose the <code>Ring</code>
     * @param fac
     *            geometry factory, primarily used to determine the max error criterion when linearizing curve segments
     */
    public IIRing(String id, ICRS crs, PrecisionModel pm, List<Curve> members, final IIGeometryFactory fac) {
        super(id, crs, pm, members);
        this.geomFactory = fac;
        this.linearizer = new CustomCurveLinearizer(geomFactory, 0.0);
    }

    /**
     * Creates a new <code>DefaultRing</code> instance from the given parameters.
     *
     * @param id
     *            identifier, may be null
     * @param crs
     *            coordinate reference system, may be null
     * @param pm
     *            precision model, may be null
     * @param segment
     *            the segment that composes the <code>Ring</code>
     * @param fac
     *            geometry factory, primarily used to determine the max error criterion when linearizing curve segments
     */
    public IIRing(String id, ICRS crs, PrecisionModel pm, LineStringSegment segment, final IIGeometryFactory fac) {
        super(id, crs, pm, segment);
        this.geomFactory = fac;
        this.linearizer = new CustomCurveLinearizer(geomFactory, 0.0);
    }

    @Override
    protected com.vividsolutions.jts.geom.LinearRing buildJTSGeometry() {

        List<Coordinate> coords = new LinkedList<Coordinate>();
        for (CurveSegment segment : segments) {
            LineStringSegment lsSegment = linearizer.linearize(segment, geomFactory.getMaxErrorCriterion());
            coords.addAll(getCoordinates(lsSegment));
        }
        return jtsFactory.createLinearRing(coords.toArray(new Coordinate[coords.size()]));
    }

    protected List<Coordinate> getCoordinates(LineStringSegment lsSegment) {
        Points points = lsSegment.getControlPoints();
        List<Coordinate> coordinates = new ArrayList<Coordinate>(points.size());
        for (Point point : points) {
            coordinates.add(new Coordinate(point.get0(), point.get1(), point.get2()));
        }
        return coordinates;
    }
}
