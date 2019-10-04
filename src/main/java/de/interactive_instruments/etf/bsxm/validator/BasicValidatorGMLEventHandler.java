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
package de.interactive_instruments.etf.bsxm.validator;

import java.util.List;

import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.segments.CurveSegment;
import org.deegree.geometry.validation.GeometryValidationEventHandler;
import org.deegree.geometry.validation.event.*;

/**
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
final class BasicValidatorGMLEventHandler implements GeometryValidationEventHandler {

    private final ValidationResult result;
    private final ElementContext elementContext;
    private boolean ignoreRingRotation;

    public BasicValidatorGMLEventHandler(final ElementContext elementContext, final ValidationResult result,
            final boolean ignoreRingRotation) {
        this.elementContext = elementContext;
        this.result = result;
        this.ignoreRingRotation = ignoreRingRotation;
    }

    @Override
    public boolean fireEvent(final GeometryValidationEvent event) {

        if (event instanceof CurveDiscontinuity) {

            return curveDiscontinuity((CurveDiscontinuity) event);
        } else if (event instanceof RingNotClosed) {

            return ringNotClosed((RingNotClosed) event);
        } else if (event instanceof CurveSelfIntersection) {
            // Currently handled by separate JTS validation
            return true;
            // return curveSelfIntersection((CurveSelfIntersection) event);
        } else if (event instanceof DuplicatePoints) {
            // Ignore.
            return true;
        } else if (event instanceof InteriorRingIntersectsExterior) {
            // Currently handled by separate JTS validation
            return true;
            // return interiorRingIntersectsExterior((InteriorRingIntersectsExterior) event);
        } else if (event instanceof InteriorRingOutsideExterior) {
            // Currently handled by separate JTS validation
            return true;
            // return interiorRingOutsideExterior((InteriorRingOutsideExterior) event);
        } else if (event instanceof InteriorRingsIntersect) {

            // Currently handled by separate JTS validation
            return true;
            // return interiorRingsIntersect((InteriorRingsIntersect) event);
        } else if (event instanceof InteriorRingsNested) {
            // Currently handled by separate JTS validation
            return true;
            // return interiorRingsWithin((InteriorRingsNested) event);
        } else if (event instanceof InteriorRingsTouch) {

            // Currently handled by separate JTS validation
            return true;
            // return interiorRingsTouch( (InteriorRingsTouch) event);
        } else if (event instanceof InteriorRingTouchesExterior) {

            // Currently handled by separate JTS validation
            return true;
            // return interiorRingTouchesExterior( (InteriorRingTouchesExterior) event);
        } else if (event instanceof ExteriorRingOrientation) {
            if (ignoreRingRotation) {
                return true;
            }

            return exteriorRingOrientation((ExteriorRingOrientation) event);
        } else if (event instanceof InteriorRingOrientation) {
            if (ignoreRingRotation) {
                return true;
            }
            return interiorRingOrientation((InteriorRingOrientation) event);
        } else {
            throw new IllegalStateException("Unknown event: " + event.getClass().getName());
        }
    }

    private boolean curveDiscontinuity(final CurveDiscontinuity evt) {
        final Curve curve = evt.getCurve();
        final int segmentIdx = evt.getEndPointSegmentIndex();

        final Point endPoint = curve.getCurveSegments().get(segmentIdx - 1).getEndPoint();
        final Point startPoint = curve.getCurveSegments().get(segmentIdx).getStartPoint();

        result.addError(elementContext, Message.translate("gmlgeox.validation.geometry.curvediscontinuity",
                Message.formatPoint(startPoint),
                segmentIdx + 1,
                Message.formatPoint(endPoint)), startPoint);
        return false;
    }

    private boolean exteriorRingOrientation(final ExteriorRingOrientation evt) {
        if (evt.isClockwise()) {
            final PolygonPatch patch = evt.getPatch();
            result.addError(elementContext, Message.translate(
                    "gmlgeox.validation.geometry.exteriorRingCW"), patch.getExteriorRing());
            return false;
        }
        return true;
    }

    private boolean interiorRingOrientation(final InteriorRingOrientation evt) {
        if (evt.isClockwise()) {
            return true;
        }
        evt.getGeometryParticleHierarchy();
        final PolygonPatch patch = evt.getPatch();
        final int ringIdx = evt.getRingIdx();
        result.addError(elementContext, Message.translate(
                "gmlgeox.validation.geometry.interiorRingCCW", ringIdx + 1), patch.getInteriorRings().get(ringIdx));
        return false;
    }

    private boolean ringNotClosed(final RingNotClosed evt) {
        final List<CurveSegment> curveSegments = evt.getRing().getCurveSegments();
        final Point startPoint = curveSegments.get(0).getStartPoint();
        final Point endPoint = curveSegments.get(curveSegments.size() - 1).getEndPoint();
        result.addError(elementContext,
                Message.translate("gmlgeox.validation.geometry.ringnotclosed",
                        Message.formatPoint(startPoint), Message.formatPoint(endPoint)),
                evt.getRing());

        return false;
    }
}
