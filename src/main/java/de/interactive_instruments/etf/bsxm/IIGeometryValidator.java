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

import com.vividsolutions.jts.algorithm.CGAlgorithms;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;

import org.deegree.geometry.Geometry;
import org.deegree.geometry.composite.CompositeGeometry;
import org.deegree.geometry.linearization.LinearizationCriterion;
import org.deegree.geometry.multi.MultiGeometry;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.GeometricPrimitive;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.Ring;
import org.deegree.geometry.primitive.Surface;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.patches.SurfacePatch;
import org.deegree.geometry.primitive.segments.Arc;
import org.deegree.geometry.primitive.segments.Circle;
import org.deegree.geometry.primitive.segments.CurveSegment;
import org.deegree.geometry.primitive.segments.CurveSegment.CurveSegmentType;
import org.deegree.geometry.primitive.segments.LineStringSegment;
import org.deegree.geometry.validation.GeometryValidationEventHandler;
import org.deegree.geometry.validation.GeometryValidator;
import org.deegree.geometry.validation.event.CurveDiscontinuity;
import org.deegree.geometry.validation.event.DuplicatePoints;
import org.deegree.geometry.validation.event.ExteriorRingOrientation;
import org.deegree.geometry.validation.event.GeometryValidationEvent;
import org.deegree.geometry.validation.event.InteriorRingOrientation;
import org.deegree.geometry.validation.event.RingNotClosed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Geometry validator, loosely based on the according deegree class. This class is optimized for GmlGeoX (does not compute validation events that are ignored by the GMLValidationEventHandler, also performs orientation checks [and linearization for such checks] for a given geometry only if it is valid according to JTS validation).
 * <p>
 * With this class, the issue of incorrect results when checking the orientation of surface boundaries whose coordinates are given in a left-handed CRS could also be fixed. For more details, see https://github.com/deegree/deegree3/issues/886.
 */
public class IIGeometryValidator {

    private static final Logger LOG = LoggerFactory.getLogger(IIGeometryValidator.class);

    protected CustomCurveLinearizer linearizer;

    protected LinearizationCriterion crit;

    protected IIGeometryFactory geomFac;

    protected GeometryFactory jtsFactory = new GeometryFactory();

    protected GeometryValidationEventHandler eventHandler;

    /**
     * Creates a new {@link GeometryValidator} which performs callbacks on the given {@link GeometryValidationEventHandler} in case of errors.
     *
     * @param eventHandler
     *            callback handler for errors, must not be <code>null</code>
     */
    public IIGeometryValidator(GeometryValidationEventHandler eventHandler, IIGeometryFactory geomFac) {
        this.eventHandler = eventHandler;
        this.geomFac = geomFac;
        this.linearizer = new CustomCurveLinearizer(geomFac, 0.0);
        this.crit = geomFac.getMaxErrorCriterion();
    }

    /**
     * Validates the given {@link Geometry}.
     * <p>
     * Contained geometry objects and geometry particles are recursively checked (e.g. the members of a {@link MultiGeometry}) and callbacks to the associated {@link GeometryValidationEventHandler} are performed for each detected issue.
     *
     * @param geom
     *            geometry to be validated
     * @return true, if the geometry is valid, false otherwise (depends on the {@link GeometryValidationEventHandler} implementation)
     */
    public boolean validateGeometry(Geometry geom, boolean jtsValidationSucceeded) {
        return validateGeometry(geom, new ArrayList<Object>(), jtsValidationSucceeded);
    }

    private boolean validateGeometry(Geometry geom, List<Object> affectedGeometryParticles,
            boolean jtsValidationSucceeded) {

        boolean isValid = false;
        switch (geom.getGeometryType()) {
        case COMPOSITE_GEOMETRY: {
            isValid = validate((CompositeGeometry<?>) geom, affectedGeometryParticles, jtsValidationSucceeded);
            break;
        }
        case ENVELOPE: {
            String msg = "Internal error: envelope 'geometries' should not occur here.";
            throw new IllegalArgumentException(msg);
        }
        case MULTI_GEOMETRY: {
            isValid = validate((MultiGeometry<?>) geom, affectedGeometryParticles, jtsValidationSucceeded);
            break;
        }
        case PRIMITIVE_GEOMETRY: {
            isValid = validate((GeometricPrimitive) geom, affectedGeometryParticles, jtsValidationSucceeded);
            break;
        }
        }
        return isValid;
    }

    private boolean validate(GeometricPrimitive geom, List<Object> affectedGeometryParticles,
            boolean jtsValidationSucceeded) {
        boolean isValid = true;
        switch (geom.getPrimitiveType()) {
        case Point: {
            LOG.debug("Point geometry. No validation necessary.");
            break;
        }
        case Curve: {
            isValid = validateCurve((Curve) geom, affectedGeometryParticles);
            break;
        }
        case Surface: {
            isValid = validateSurface((Surface) geom, affectedGeometryParticles, jtsValidationSucceeded);
            break;
        }
        case Solid: {
            String msg = "Validation of solids is not available";
            throw new IllegalArgumentException(msg);
        }
        }
        return isValid;
    }

    /**
     * Checks for duplicate control points and discontinuous segments in curves. If the curve is a ring, it is also checked whether the ring is closed or not.
     *
     * @param curve
     * @param affectedGeometryParticles
     * @return
     */
    private boolean validateCurve(Curve curve, List<Object> affectedGeometryParticles) {

        boolean isValid = true;

        List<Object> affectedGeometryParticles2 = new ArrayList<Object>(affectedGeometryParticles);
        affectedGeometryParticles2.add(curve);

        LOG.debug("Curve geometry. Testing for duplication of successive control points.");

        int segmentIdx = 0;
        for (CurveSegment segment : curve.getCurveSegments()) {
            if (segment.getSegmentType() == CurveSegmentType.LINE_STRING_SEGMENT) {
                LineStringSegment lineStringSegment = (LineStringSegment) segment;
                Point lastPoint = null;
                for (Point point : lineStringSegment.getControlPoints()) {
                    if (lastPoint != null) {
                        if (point.equals(lastPoint)) {
                            LOG.debug("Found duplicate control points.");
                            if (!fireEvent(new DuplicatePoints(curve, point, affectedGeometryParticles2))) {
                                isValid = false;
                            }
                        }
                    }
                    lastPoint = point;
                }
            } else {
                LOG.warn("Non-linear curve segment. Skipping check for duplicate control points.");
            }
            segmentIdx++;
        }

        LOG.debug("Curve geometry. Testing segment continuity.");
        Point lastSegmentEndPoint = null;
        segmentIdx = 0;
        for (CurveSegment segment : curve.getCurveSegments()) {
            Point startPoint = segment.getStartPoint();
            if (lastSegmentEndPoint != null) {
                if (startPoint.get0() != lastSegmentEndPoint.get0()
                        || startPoint.get1() != lastSegmentEndPoint.get1()) {
                    LOG.debug("Found discontinuous segments.");
                    if (!fireEvent(new CurveDiscontinuity(curve, segmentIdx, affectedGeometryParticles2))) {
                        isValid = false;
                    }
                }
            }
            segmentIdx++;
            lastSegmentEndPoint = segment.getEndPoint();
        }

        if (curve instanceof Ring) {
            LOG.debug("Ring geometry. Testing if it's closed. ");
            if (!curve.isClosed()) {
                LOG.debug("Not closed.");
                if (!fireEvent(new RingNotClosed((Ring) curve, affectedGeometryParticles2))) {
                    isValid = false;
                }
            }
        }

        /* 2019-09-18 JE: CurveSelfIntersection check is ignored by the GMLValidationEventHandler, since self intersection checks are performed separately (by the JTS validation). No need to perform the self intersection test here, in this geometry validator. */

        return isValid;
    }

    private boolean validateSurface(Surface surface, List<Object> affectedGeometryParticles,
            boolean jtsValidationSucceeded) {
        LOG.debug("Surface geometry. Validating individual patches.");
        boolean isValid = true;
        List<Object> affectedGeometryParticles2 = new ArrayList<Object>(affectedGeometryParticles);
        affectedGeometryParticles2.add(surface);

        List<? extends SurfacePatch> patches = surface.getPatches();
        if (patches.size() > 1) {
            LOG.warn(
                    "Surface consists of multiple patches, but validation of inter-patch topology is not available yet.");
        }
        for (SurfacePatch patch : surface.getPatches()) {
            if (!(patch instanceof PolygonPatch)) {
                LOG.warn("Skipping validation of surface patch -- not a PolygonPatch.");
            } else {
                if (!validatePatch((PolygonPatch) patch, affectedGeometryParticles2, jtsValidationSucceeded)) {
                    isValid = false;
                }
            }
        }
        return isValid;
    }

    private boolean validatePatch(PolygonPatch patch, List<Object> affectedGeometryParticles,
            boolean jtsValidationSucceeded) {

        boolean isValid = true;
        List<Object> affectedGeometryParticles2 = new ArrayList<Object>(affectedGeometryParticles);
        affectedGeometryParticles2.add(patch);
        LOG.debug("Surface patch. Validating rings and spatial ring relations.");

        try {

            // validate the curve of the exterior ring
            Ring exteriorRing = patch.getExteriorRing();
            if (!validateCurve(exteriorRing, affectedGeometryParticles2)) {
                isValid = false;
            }

            /* 2019-09-17 JE: CGAlgorithms.isCCW(..) is only guaranteed to work for valid rings. If a ring is not valid, then the result of isCCW(..) is not reliable.
             *
             * Note that axis orientation plays a role for CGAlgorithms.isCCW(..). It assumes a right-handed coordinate reference system. TODO If the ring is given in a left-handed CRS, the ring coordinates need to be transformed. Realizing such a transformation is future work.
             *
             * An explanation for why CGAlgorithms.isCCW(..) would return false (in unit test test_arc_interpolation_self_intersection for test surface with gml:id=DETHL56P00019ALS) when our custom linearizer is used would be that the result of isCCW(..) is wrong because the linearized ring is invalid (due to the self-intersection).
             *
             * We perform an orientation check only if the geometry is valid (based upon the result of the JTS validation). The orientation checks are performed based upon the results of our custom linearization (because with a different linearization the resulting JTS geometry could be invalid). */

            // Only perform the orientation test if JTS validation found out that the
            // geometry is valid
            if (jtsValidationSucceeded) {

                // transform exterior ring to linearized JTS geometry
                LinearRing exteriorJTSRing = getJTSRing(exteriorRing);

                LOG.debug("Surface patch. Validating exterior ring orientation.");

                boolean isClockwise = !CGAlgorithms.isCCW(exteriorJTSRing.getCoordinates());

                if (!fireEvent(new ExteriorRingOrientation(patch, isClockwise, affectedGeometryParticles2))) {
                    isValid = false;
                }
            }

            List<Ring> interiorRings = patch.getInteriorRings();
            int interiorRingIdx = 0;
            for (Ring interiorRing : interiorRings) {

                // validate curve of interior ring
                if (!validateCurve(interiorRing, affectedGeometryParticles2)) {
                    isValid = false;
                }

                // Only perform the orientation test if JTS validation found out that the
                // geometry is valid
                if (jtsValidationSucceeded) {

                    // transform interior ring to linearized JTS geometries
                    LinearRing interiorJTSRing = getJTSRing(interiorRing);
                    LOG.debug("Surface patch. Validating interior ring orientation.");
                    boolean isClockwise = !CGAlgorithms.isCCW(interiorJTSRing.getCoordinates());
                    if (!fireEvent(new InteriorRingOrientation(patch, interiorRingIdx++, isClockwise,
                            affectedGeometryParticles2))) {
                        isValid = false;
                    }
                }
            }

            /* 2019-09-18 JE: Validation events InteriorRingIntersectsExterior, InteriorRingOutsideExterior, InteriorRingsIntersect, InteriorRingsNested, InteriorRingsTouch, and InteriorRingTouchesExterior are ignored by the GMLValidationEventHandler. These events are handled by the JTS validation. Thus, no need to compute such events here. */

        } catch (Exception e) {
            LOG.debug("Validation interrupted: " + e.getMessage());
        }

        return isValid;
    }

    private boolean validate(CompositeGeometry<?> geom, List<Object> affectedGeometryParticles,
            boolean jtsValidationSucceeded) {
        LOG.debug("Composite geometry. Validating individual member geometries.");
        LOG.warn("Composite geometry found, but validation of inter-primitive topology is not available yet.");
        boolean isValid = true;
        List<Object> affectedGeometryParticles2 = new ArrayList<Object>(affectedGeometryParticles);
        affectedGeometryParticles2.add(geom);
        for (GeometricPrimitive geometricPrimitive : geom) {
            if (!validate(geometricPrimitive, affectedGeometryParticles2, jtsValidationSucceeded)) {
                isValid = false;
            }
        }
        return isValid;
    }

    private boolean validate(MultiGeometry<?> geom, List<Object> affectedGeometryParticles,
            boolean jtsValidationSucceeded) {
        LOG.debug("MultiGeometry. Validating individual member geometries.");
        boolean isValid = true;
        List<Object> affectedGeometryParticles2 = new ArrayList<Object>(affectedGeometryParticles);
        affectedGeometryParticles2.add(geom);
        for (Geometry member : geom) {
            if (!validateGeometry(member, affectedGeometryParticles2, jtsValidationSucceeded)) {
                isValid = false;
            }
        }
        return isValid;
    }

    /**
     * Returns a JTS geometry for the given {@link Ring} (which is linearized first).
     *
     * @param ring
     *            {@link Ring} that consists of {@link LineStringSegment}, {@link Arc} and {@link Circle} segments only
     * @return linear JTS ring geometry, null if no
     * @throws IllegalArgumentException
     *             if the given input ring contains other segment types than {@link LineStringSegment}, {@link Arc} and {@link Circle}
     */
    private LinearRing getJTSRing(Ring ring) {

        Ring linearizedRing = (Ring) this.linearizer.linearize(ring, this.crit);
        List<Coordinate> coordinates = new LinkedList<Coordinate>();
        for (Curve member : linearizedRing.getMembers()) {
            for (CurveSegment segment : member.getCurveSegments()) {
                for (Point point : ((LineStringSegment) segment).getControlPoints()) {
                    coordinates.add(new Coordinate(point.get0(), point.get1()));
                }
            }
        }
        return this.jtsFactory.createLinearRing(coordinates.toArray(new Coordinate[coordinates.size()]));
    }

    private boolean fireEvent(GeometryValidationEvent event) {
        return this.eventHandler.fireEvent(event);
    }

}
