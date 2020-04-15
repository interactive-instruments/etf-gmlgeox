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
package de.interactive_instruments.etf.bsxm.geometry;

import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;

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

    private final IIGeometryFactory geomFactory;
    private final CustomCurveLinearizer linearizer;

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
    IICurve(final String id, final ICRS crs, final PrecisionModel pm,
            final List<CurveSegment> segments, final IIGeometryFactory fac) {
        super(id, crs, pm, segments);
        this.geomFactory = fac;
        this.linearizer = new CustomCurveLinearizer(geomFactory, 0.0);
    }

    @Override
    public com.vividsolutions.jts.geom.LineString buildJTSGeometry() {
        final List<CurveSegment> originalSegments = getCurveSegments();
        final List<CurveSegment> segments;
        boolean clockwise = isClockwise(originalSegments);
        if (clockwise) {
            segments = originalSegments;
        } else {
            segments = new ListRevWrapper<>(originalSegments);
        }
        final int segmentsSize = segments.size();
        final ArrayList<Coordinate[]> coordinates = new ArrayList<>(segmentsSize);
        coordinates.add(linearize(segments.get(0), clockwise).getControlPoints().toCoordinateArray());
        int arrSize = coordinates.get(0).length;
        for (int i = 1; i < segmentsSize; i++) {
            coordinates.add(linearize(segments.get(i), clockwise).getControlPoints().toCoordinateArray());
            arrSize += coordinates.get(i).length - 1;
        }
        final Coordinate[] coorArray = new Coordinate[arrSize];
        System.arraycopy(coordinates.get(0), 0, coorArray, 0, coordinates.get(0).length);
        for (int i = 1, previousSize = coordinates.get(0).length; i < coordinates.size(); i++) {
            System.arraycopy(coordinates.get(i), 1, coorArray, previousSize, coordinates.get(i).length - 1);
            previousSize += coordinates.get(i).length - 1;
        }
        final LineString lineString = jtsFactory.createLineString(coorArray);
        return clockwise ? lineString : (LineString) lineString.reverse();
    }

    private LineStringSegment linearize(final CurveSegment segment,
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
        return lineSegment;
    }

    private static boolean isClockwise(final ArcString curve) {
        final Point center = curve.getControlPoints().get(1);
        return (curve.getStartPoint().get(0) - center.get0())
                * (curve.getEndPoint().get1() - center.get1())
                - (curve.getStartPoint().get(1) - center.get1())
                        * (curve.getEndPoint().get0() - center.get0()) > 0;
    }

    private boolean isClockwise(final List<CurveSegment> segments) {
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
