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
package de.interactive_instruments.etf.bsxm.geometry;

import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.segments.LineStringSegment;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class LineStringRevWrapper implements LineStringSegment {

    private final LineStringSegment wrappedLineStringSegment;

    public LineStringRevWrapper(final LineStringSegment segment) {
        this.wrappedLineStringSegment = segment;
    }

    @Override
    public Points getControlPoints() {
        return new PointsRevWrapper(wrappedLineStringSegment.getControlPoints());
    }

    @Override
    public CurveSegmentType getSegmentType() {
        return wrappedLineStringSegment.getSegmentType();
    }

    @Override
    public int getCoordinateDimension() {
        return wrappedLineStringSegment.getCoordinateDimension();
    }

    @Override
    public Point getStartPoint() {
        return wrappedLineStringSegment.getEndPoint();
    }

    @Override
    public Point getEndPoint() {
        return wrappedLineStringSegment.getStartPoint();
    }
}
