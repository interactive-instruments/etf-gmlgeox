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
import org.deegree.geometry.Geometry;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.precision.PrecisionModel;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.segments.Arc;
import org.deegree.geometry.primitive.segments.ArcString;
import org.deegree.geometry.primitive.segments.CurveSegment;
import org.deegree.geometry.primitive.segments.LineStringSegment;
import org.deegree.geometry.standard.primitive.DefaultCurve;

/**
 * Based on the implementation of {@link DefaultCurve}.
 */
public class IICurve extends DefaultCurve {

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
     * @param segments
     *            segments that belong to the curve
     * @param fac
     *            geometry factory, primarily used to determine the max error criterion when linearizing curve segments
     */
    public IICurve(final String id, final ICRS crs, final PrecisionModel pm,
            final List<CurveSegment> segments, final IIGeometryFactory fac) {
        super(id, crs, pm, segments);
        this.geomFactory = fac;
        this.linearizer = new CustomCurveLinearizer(geomFactory, 0.0);
    }

    @Override
    protected com.vividsolutions.jts.geom.LineString buildJTSGeometry() {
        final List<Coordinate> coords = new LinkedList<>();

        final List<CurveSegment> segments;
        boolean clockwise = isClockwise();
        if (clockwise) {
            segments = getCurveSegments();
        } else {
            segments = new ListRevWrapper<>(getCurveSegments());
        }
        boolean first = true;
        for (CurveSegment segment : segments) {
            final List<Coordinate> coordinates = linearize(segment, clockwise);
            if (first) {
                coords.addAll(coordinates);
                first = false;
            } else {
                // starting with the second segment, skip the first point (as it
                // *must* be identical to
                // last point of the last segment)
                coords.addAll(coordinates.subList(1, coordinates.size()));
            }
        }
        if (clockwise) {
            return jtsFactory.createLineString(
                    coords.toArray(new Coordinate[coords.size()]));
        } else {
            return jtsFactory.createLineString(new ListRevWrapper<>(coords)
                    .toArray(new Coordinate[coords.size()]));
        }
    }

    private List<Coordinate> linearize(final CurveSegment segment,
            boolean clockwise) {

        final LineStringSegment lineSegment;
        switch (segment.getSegmentType()) {
        case ARC:
        case CIRCLE: {
            final Arc normalized;
            if (clockwise) {
                normalized = (Arc) segment;
            } else {
                normalized = new ArcRevWrapper((Arc) segment);
            }
            lineSegment = linearizer.linearizeArc(normalized,
                    geomFactory.getMaxErrorCriterion());
        }
            break;
        case ARC_STRING: {
            final ArcString normalized;
            if (clockwise) {
                normalized = (ArcString) segment;
            } else {
                normalized = new ArcStringRevWrapper((ArcString) segment);
            }
            lineSegment = linearizer.linearizeArcString(normalized,
                    geomFactory.getMaxErrorCriterion());
        }
            break;
        case LINE_STRING_SEGMENT: {
            final LineStringSegment normalized;
            if (clockwise) {
                normalized = (LineStringSegment) segment;
            } else {
                normalized = new LineStringRevWrapper(
                        (LineStringSegment) segment);
            }
            lineSegment = normalized;
            break;
        }
        default:
            throw new IllegalStateException("not implemented");
        }
        return getCoordinates(lineSegment);
    }

    private List<Coordinate> getCoordinates(LineStringSegment lsSegment) {
        final Points points = lsSegment.getControlPoints();
        final List<Coordinate> coordinates = new ArrayList<>(points.size());
        for (Point point : points) {
            coordinates.add(
                    new Coordinate(point.get0(), point.get1(), point.get2()));
        }
        return coordinates;
    }

    private boolean isClockwise(final ArcString curve) {
        final Point center = curve.getControlPoints().get(1);
        return (curve.getStartPoint().get(0) - center.get0())
                * (curve.getEndPoint().get1() - center.get1())
                - (curve.getStartPoint().get(1) - center.get1())
                        * (curve.getEndPoint().get0() - center.get0()) > 0;
    }

    public boolean isClockwise() {
        final List<CurveSegment> segments = getCurveSegments();
        for (final CurveSegment curve : segments) {
            if (curve instanceof ArcString) {
                return isClockwise((ArcString) curve);
            }
        }
        double cs = 0;
        final Points cps = getControlPoints();
        for (int i = 1; i < cps.size(); i++) {
            cs += (cps.get(i).get0() - cps.get(i - 1).get0())
                    * (cps.get(i).get1() - cps.get(i - 1).get1());
        }
        return cs > 0;
    }

    @Override
    public boolean equals(final Geometry geometry) {
        if (geometry instanceof IICurve) {
            final List<CurveSegment> segments = this.getCurveSegments();
            final List<CurveSegment> otherSegments = ((IICurve) geometry)
                    .getCurveSegments();
            if (segments.size() == 1 && otherSegments.size() == 1) {
                final CurveSegment curve = segments.get(0);
                final CurveSegment otherCurve = otherSegments.get(0);
                if (curve instanceof ArcString
                        && otherCurve instanceof ArcString) {
                    final Points controlPoints = ((ArcString) curve)
                            .getControlPoints();
                    final Points otherControlPoints = ((ArcString) otherCurve)
                            .getControlPoints();
                    return (controlPoints.get(1)
                            .equals(otherControlPoints.get(1))
                            && ((controlPoints.get(0)
                                    .equals(otherControlPoints.get(0))
                                    && controlPoints.get(2)
                                            .equals(otherControlPoints.get(2))))
                            || (controlPoints.get(2)
                                    .equals(otherControlPoints.get(0))
                                    && controlPoints.get(2).equals(
                                            otherControlPoints.get(0))));
                }
            }

        }
        return super.equals(geometry);
    }
}
