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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.segments.ArcString;
import org.deegree.geometry.primitive.segments.Circle;
import org.deegree.geometry.primitive.segments.LineStringSegment;
import org.deegree.geometry.standard.AbstractDefaultGeometry;
import org.deegree.geometry.standard.JTSGeometryPair;
import org.junit.Test;

public final class DegreeArcTest {

    @Test
    public void testIntersects2() {
        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        final Point arc1P1 = geomFactory.createPoint("arc1P1", 1.0, 0.0, crs);
        final Point arc1P2 = geomFactory.createPoint("arc1P2", 0.0, 1.0, crs);
        final Point arc1P3 = geomFactory.createPoint("arc1P3", -1.0, 0.0, crs);
        final List<Point> arcPointList = new ArrayList<>();
        arcPointList.add(arc1P1);
        arcPointList.add(arc1P2);
        arcPointList.add(arc1P3);
        final Curve arcCurve = geomFactory.createCurve("arc", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList)));

        final Point lsP1 = geomFactory.createPoint("lsP1", -1.0, 1.0, crs);
        final Point lsP2 = geomFactory.createPoint("lsP2", 1.0, 1.0, crs);
        final List<Point> lsPointList = new ArrayList<>();
        lsPointList.add(lsP1);
        lsPointList.add(lsP2);
        final Curve lsCurve = geomFactory.createCurve("LS", crs,
                geomFactory.createLineStringSegment(geomFactory.createPoints(lsPointList)));

        final JTSGeometryPair jtsGeoms = JTSGeometryPair.createCompatiblePair((AbstractDefaultGeometry) arcCurve,
                lsCurve);

        System.out.println(jtsGeoms.first);
        System.out.println(jtsGeoms.second);

        assertTrue(arcCurve.intersects(lsCurve));
    }

    @Test
    public void testIntersects3() {
        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        final Point arc1P1 = geomFactory.createPoint("arc1P1", 1.0, 0.0, crs);
        final Point arc1P2 = geomFactory.createPoint("arc1P2", 0.0, 1.0, crs);
        final Point arc1P3 = geomFactory.createPoint("arc1P3", -1.0, 0.0, crs);
        final List<Point> arcPointList = new ArrayList<>();
        arcPointList.add(arc1P1);
        arcPointList.add(arc1P2);
        arcPointList.add(arc1P3);
        final Curve arcCurve = geomFactory.createCurve("arc", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList)));

        final Point lsP1 = geomFactory.createPoint("lsP1", 0.378310065149095, 1.094648792904952, crs);
        final Point lsP2 = geomFactory.createPoint("lsP2", 1.093793305137216, 0.260974580191861, crs);
        final List<Point> lsPointList = new ArrayList<>();
        lsPointList.add(lsP1);
        lsPointList.add(lsP2);
        final Curve lsCurve = geomFactory.createCurve("LS", crs,
                geomFactory.createLineStringSegment(geomFactory.createPoints(lsPointList)));

        assertTrue(arcCurve.intersects(lsCurve));
    }

    @Test
    public void testIntersects4() {
        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        final Point arc1P1 = geomFactory.createPoint("arc1P1", 1.0, 0.0, crs);
        final Point arc1P2 = geomFactory.createPoint("arc1P2", 0.0, 1.0, crs);
        final Point arc1P3 = geomFactory.createPoint("arc1P3", -1.0, 0.0, crs);
        final List<Point> arcPointList = new ArrayList<>();
        arcPointList.add(arc1P1);
        arcPointList.add(arc1P2);
        arcPointList.add(arc1P3);
        final Curve arcCurve = geomFactory.createCurve("arc", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList)));

        final Point lsP1 = geomFactory.createPoint("lsP1", 0.635092918570922, 0.777252403790242, crs);
        final Point lsP2 = geomFactory.createPoint("lsP2", 0.468543903140246, 0.890943937416946, crs);
        final List<Point> lsPointList = new ArrayList<>();
        lsPointList.add(lsP1);
        lsPointList.add(lsP2);
        final Curve lsCurve = geomFactory.createCurve("LS", crs,
                geomFactory.createLineStringSegment(geomFactory.createPoints(lsPointList)));

        assertFalse(lsCurve.intersects(arcCurve));
    }

    @Test
    public void testEquals_ArcString() {

        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        final Point arc1P1 = geomFactory.createPoint("arc1P1", 1.0, 0.0, crs);
        final Point arc1P2 = geomFactory.createPoint("arc1P2", 0.0, 1.0, crs);
        final Point arc1P3 = geomFactory.createPoint("arc1P3", -1.0, 0.0, crs);
        final List<Point> arcPointList1 = new ArrayList<>();
        arcPointList1.add(arc1P1);
        arcPointList1.add(arc1P2);
        arcPointList1.add(arc1P3);
        final Curve arcCurve1 = geomFactory.createCurve("arc1", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList1)));

        final Point arc2P1 = geomFactory.createPoint("arc2P1", -1.0, 0.0, crs);
        final Point arc2P2 = geomFactory.createPoint("arc2P2", 0.0, 1.0, crs);
        final Point arc2P3 = geomFactory.createPoint("arc2P3", 1.0, 0.0, crs);
        final List<Point> arcPointList2 = new ArrayList<>();
        arcPointList2.add(arc2P1);
        arcPointList2.add(arc2P2);
        arcPointList2.add(arc2P3);
        final Curve arcCurve2 = geomFactory.createCurve("arc2", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList2)));

        // Control point check
        assertTrue(arcCurve1.equals(arcCurve2));
        final JTSGeometryPair jtsGeoms = JTSGeometryPair.createCompatiblePair((AbstractDefaultGeometry) arcCurve1,
                arcCurve2);
        // Check linearization
        assertTrue(jtsGeoms.first.equalsNorm(jtsGeoms.second));
        assertTrue(jtsGeoms.first.equals(jtsGeoms.second));
    }

    @Test
    public void testEquals_Circle() {

        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        final Point g1P1 = geomFactory.createPoint("g1P1", 1.0, 0.0, crs);
        final Point g1P2 = geomFactory.createPoint("g1P2", 0.0, 1.0, crs);
        final Point g1P3 = geomFactory.createPoint("g1P3", -1.0, 0.0, crs);
        Circle circle1 = geomFactory.createCircle(g1P1, g1P2, g1P3);

        final Curve arcCurve1 = geomFactory.createCurve("c1", crs, circle1);

        final Point g2P1 = geomFactory.createPoint("g2P1", -1.0, 0.0, crs);
        final Point g2P2 = geomFactory.createPoint("g2P2", 0.0, 1.0, crs);
        final Point g2P3 = geomFactory.createPoint("g2P3", 1.0, 0.0, crs);
        Circle circle2 = geomFactory.createCircle(g2P1, g2P2, g2P3);

        final Curve arcCurve2 = geomFactory.createCurve("c2", crs, circle2);

        assertTrue(arcCurve1.equals(arcCurve2));
    }

    @Test
    public void testNotEqual() {

        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        final Point arc1P1 = geomFactory.createPoint("arc1P1", 1.0, 1.0, crs);
        final Point arc1P2 = geomFactory.createPoint("arc1P2", 0.0, 1.0, crs);
        final Point arc1P3 = geomFactory.createPoint("arc1P3", -1.0, 0.0, crs);
        final List<Point> arcPointList1 = new ArrayList<>();
        arcPointList1.add(arc1P1);
        arcPointList1.add(arc1P2);
        arcPointList1.add(arc1P3);
        final Curve arcCurve1 = geomFactory.createCurve("arc1", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList1)));

        final Point arc2P1 = geomFactory.createPoint("arc2P1", -1.0, -1.0, crs);
        final Point arc2P2 = geomFactory.createPoint("arc2P2", 0.0, 1.0, crs);
        final Point arc2P3 = geomFactory.createPoint("arc2P3", 1.0, 0.0, crs);
        final List<Point> arcPointList2 = new ArrayList<>();
        arcPointList2.add(arc2P1);
        arcPointList2.add(arc2P2);
        arcPointList2.add(arc2P3);
        final Curve arcCurve2 = geomFactory.createCurve("arc2", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList2)));

        // Control point check
        assertFalse(arcCurve1.equals(arcCurve2));
        final JTSGeometryPair jtsGeoms = JTSGeometryPair.createCompatiblePair((AbstractDefaultGeometry) arcCurve1,
                arcCurve2);
        // Check linearization
        assertFalse(jtsGeoms.first.equalsNorm(jtsGeoms.second));
        assertFalse(jtsGeoms.first.equals(jtsGeoms.second));
    }

    @Test
    public void testOverlaps1() {

        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        final Point arc1P1 = geomFactory.createPoint("arc1P1", 1.0, 0.0, crs);
        final Point arc1P2 = geomFactory.createPoint("arc1P2", 0.0, 1.0, crs);
        final Point arc1P3 = geomFactory.createPoint("arc1P3", -1.0, 0.0, crs);
        final List<Point> arcPointList1 = new ArrayList<>();
        arcPointList1.add(arc1P1);
        arcPointList1.add(arc1P2);
        arcPointList1.add(arc1P3);

        final Point ls1P1 = geomFactory.createPoint("line1P1", -1.0, 0.0, crs);
        final Point ls1P2 = geomFactory.createPoint("line1P2", -1.0, -1.0, crs);
        final List<Point> line1PointList = new ArrayList<>();
        line1PointList.add(ls1P1);
        line1PointList.add(ls1P2);
        LineStringSegment line1 = geomFactory.createLineStringSegment(geomFactory.createPoints(line1PointList));

        final Curve curve1 = geomFactory.createCurve("c1", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList1)), line1);

        final Point arc2P1 = geomFactory.createPoint("arc2P1", -1.0, 0.0, crs);
        final Point arc2P2 = geomFactory.createPoint("arc2P2", 0.0, 1.0, crs);
        final Point arc2P3 = geomFactory.createPoint("arc2P3", 1.0, 0.0, crs);
        final List<Point> arcPointList2 = new ArrayList<>();
        arcPointList2.add(arc2P1);
        arcPointList2.add(arc2P2);
        arcPointList2.add(arc2P3);

        final Point ls2P1 = geomFactory.createPoint("line2P1", 1.0, 0.0, crs);
        final Point ls2P2 = geomFactory.createPoint("line2P2", 1.0, -1.0, crs);
        final List<Point> line2PointList = new ArrayList<>();
        line2PointList.add(ls2P1);
        line2PointList.add(ls2P2);
        LineStringSegment line2 = geomFactory.createLineStringSegment(geomFactory.createPoints(line2PointList));

        final Curve curve2 = geomFactory.createCurve("c2", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList2)), line2);

        assertTrue(curve1.overlaps(curve2));
    }

    /**
     * Check overlap when arc strings are actually line strings because the control points of the arcs are collinear.
     */
    @Test
    public void testOverlaps2_collinearPoints() {

        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        /* Collinear points */
        final Point arc1P1 = geomFactory.createPoint("arc1P1", 1.0, 0.0, crs);
        final Point arc1P2 = geomFactory.createPoint("arc1P2", 0.0, 0.0, crs);
        final Point arc1P3 = geomFactory.createPoint("arc1P3", -1.0, 0.0, crs);
        final List<Point> arcPointList1 = new ArrayList<>();
        arcPointList1.add(arc1P1);
        arcPointList1.add(arc1P2);
        arcPointList1.add(arc1P3);

        final Point ls1P1 = geomFactory.createPoint("line1P1", -1.0, 0.0, crs);
        final Point ls1P2 = geomFactory.createPoint("line1P2", -1.0, -1.0, crs);
        final List<Point> line1PointList = new ArrayList<>();
        line1PointList.add(ls1P1);
        line1PointList.add(ls1P2);
        LineStringSegment line1 = geomFactory.createLineStringSegment(geomFactory.createPoints(line1PointList));

        final Curve curve1 = geomFactory.createCurve("c1", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList1)), line1);

        /* Collinear points */
        final Point arc2P1 = geomFactory.createPoint("arc2P1", -1.0, 0.0, crs);
        final Point arc2P2 = geomFactory.createPoint("arc2P2", 0.0, 0.0, crs);
        final Point arc2P3 = geomFactory.createPoint("arc2P3", 1.0, 0.0, crs);
        final List<Point> arcPointList2 = new ArrayList<>();
        arcPointList2.add(arc2P1);
        arcPointList2.add(arc2P2);
        arcPointList2.add(arc2P3);

        final Point ls2P1 = geomFactory.createPoint("line2P1", 1.0, 0.0, crs);
        final Point ls2P2 = geomFactory.createPoint("line2P2", 1.0, -1.0, crs);
        final List<Point> line2PointList = new ArrayList<>();
        line2PointList.add(ls2P1);
        line2PointList.add(ls2P2);
        LineStringSegment line2 = geomFactory.createLineStringSegment(geomFactory.createPoints(line2PointList));

        final Curve curve2 = geomFactory.createCurve("c2", crs,
                geomFactory.createArcString(geomFactory.createPoints(arcPointList2)), line2);

        assertTrue(curve1.overlaps(curve2));
    }

    @Test
    public void testContains() {

        final ICRS crs = CRSManager.getCRSRef("EPSG:25832");
        final IIGeometryFactory geomFactory = new IIGeometryFactory();

        final Point ls1P1 = geomFactory.createPoint("line1P1", 1.0, -1.0, crs);
        final Point ls1P2 = geomFactory.createPoint("line1P2", 1.0, 0.0, crs);
        final List<Point> line1PointList = new ArrayList<>();
        line1PointList.add(ls1P1);
        line1PointList.add(ls1P2);
        LineStringSegment line1 = geomFactory.createLineStringSegment(geomFactory.createPoints(line1PointList));

        final Point arc1P1 = geomFactory.createPoint("arc1P1", 1.0, 0.0, crs);
        final Point arc1P2 = geomFactory.createPoint("arc1P2", 0.0, 1.0, crs);
        final Point arc1P3 = geomFactory.createPoint("arc1P3", -1.0, 0.0, crs);
        final List<Point> arcPointList1 = new ArrayList<>();
        arcPointList1.add(arc1P1);
        arcPointList1.add(arc1P2);
        arcPointList1.add(arc1P3);
        ArcString arcString1 = geomFactory.createArcString(geomFactory.createPoints(arcPointList1));

        final Point ls2P1 = geomFactory.createPoint("line2P1", -1.0, 0.0, crs);
        final Point ls2P2 = geomFactory.createPoint("line2P2", -1.0, -1.0, crs);
        final List<Point> line2PointList = new ArrayList<>();
        line2PointList.add(ls2P1);
        line2PointList.add(ls2P2);
        LineStringSegment line2 = geomFactory.createLineStringSegment(geomFactory.createPoints(line2PointList));

        final Curve curve1 = geomFactory.createCurve("curve1", crs, line1, arcString1, line2);

        final Point arc2P1 = geomFactory.createPoint("arc2P1", -1.0, 0.0, crs);
        final Point arc2P2 = geomFactory.createPoint("arc2P2", 0.0, 1.0, crs);
        final Point arc2P3 = geomFactory.createPoint("arc2P3", 1.0, 0.0, crs);
        final List<Point> arcPointList2 = new ArrayList<>();
        arcPointList2.add(arc2P1);
        arcPointList2.add(arc2P2);
        arcPointList2.add(arc2P3);
        ArcString arcString2 = geomFactory.createArcString(geomFactory.createPoints(arcPointList2));

        final Curve curveArcString1 = geomFactory.createCurve("curveArcString1", crs, arcString1);
        assertTrue(curve1.contains(curveArcString1));

        final Curve curveArcString2 = geomFactory.createCurve("curveArcString2", crs, arcString2);
        assertTrue(curve1.contains(curveArcString2));

        final Curve curve_line1 = geomFactory.createCurve("curveLine1", crs, line1);
        assertTrue(curve1.contains(curve_line1));

        final Curve curve_line2 = geomFactory.createCurve("curveLine2", crs, line2);
        assertTrue(curve1.contains(curve_line2));
    }

}
