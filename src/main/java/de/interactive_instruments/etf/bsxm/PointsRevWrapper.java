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

import java.util.Iterator;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.Point;
import org.jetbrains.annotations.NotNull;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class PointsRevWrapper implements Points {

    private Points wrappedPoints;

    public PointsRevWrapper(final Points controlPoints) {
        this.wrappedPoints = controlPoints;
    }

    @Override
    public int getDimension() {
        return wrappedPoints.getDimension();
    }

    @Override
    public int size() {
        return wrappedPoints.size();
    }

    @Override
    public Point get(final int i) {
        return wrappedPoints.get(wrappedPoints.size() - i - 1);
    }

    @Override
    public Point getStartPoint() {
        return wrappedPoints.getEndPoint();
    }

    @Override
    public Point getEndPoint() {
        return wrappedPoints.getStartPoint();
    }

    @Override
    public double[] getAsArray() {
        throw new IllegalStateException("not implemented");
    }

    @NotNull
    @Override
    public Iterator<Point> iterator() {
        return new Iterator<Point>() {
            private int pos = size();

            @Override
            public boolean hasNext() {
                return pos > 0;
            }

            @Override
            public Point next() {
                return wrappedPoints.get(--pos);
            }
        };
    }

    @Override
    public Coordinate getCoordinate(final int i) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Coordinate getCoordinateCopy(final int i) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public void getCoordinate(final int index, final Coordinate coord) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public double getX(final int index) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public double getY(final int index) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public double getOrdinate(final int index, final int ordinateIndex) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public void setOrdinate(final int index, final int ordinateIndex, final double value) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Coordinate[] toCoordinateArray() {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Envelope expandEnvelope(final Envelope env) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public Object clone() {
        throw new IllegalStateException("not implemented");
    }
}
