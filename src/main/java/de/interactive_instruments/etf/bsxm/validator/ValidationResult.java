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

import static de.interactive_instruments.etf.bsxm.validator.Message.*;
import static de.interactive_instruments.etf.bsxm.validator.ValidationReport.ERROR_QNM;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.vividsolutions.jts.geom.Coordinate;

import org.basex.query.util.list.ANodeList;
import org.basex.query.value.node.FElem;
import org.basex.query.value.node.FTxt;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.Point;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class ValidationResult {

    private byte result;
    private static byte[] coordinatesArgument = "coordinates".getBytes();
    private final List<FElem> messages;

    @Contract(pure = true)
    public ValidationResult() {
        this.result = 'V';
        this.messages = new ArrayList<>();
    }

    void failSilently() {
        this.result = 'F';
    }

    void failWith(@NotNull final Exception e) {
        this.result = 'F';
        final ANodeList children = new ANodeList(1).add(
                Message.exception(e).toNodeList(null, null));
        this.messages.add(new FElem(ERROR_QNM, null, children, null));
    }

    void addError(@NotNull final ElementContext elementContext, @NotNull final Message message) {
        this.result = 'F';
        final ANodeList children = new ANodeList(2).add(
                message.toNodeList(null, elementContext.getContextLocation()));
        this.messages.add(new FElem(ERROR_QNM, null, children, null));
    }

    void addError(@NotNull final ElementContext elementContext, @NotNull final Message message,
            @NotNull final org.deegree.geometry.Geometry affectedGeometry) {
        this.result = 'F';
        final ANodeList children = new ANodeList(3).add(
                message.toNodeList(
                        wrapCoordinates(coordinates(affectedGeometry)),
                        elementContext.getContextLocation()));
        this.messages.add(new FElem(ERROR_QNM, null, children, null));
    }

    void addError(@NotNull final ElementContext elementContext, @NotNull final Message message,
            final com.vividsolutions.jts.geom.Geometry affectedGeometry, final Coordinate coordProblem) {
        this.result = 'F';
        final ANodeList children = message.toNodeList(
                wrapCoordinates(coordinates(affectedGeometry, coordProblem)),
                elementContext.getContextLocation());
        this.messages.add(new FElem(ERROR_QNM, null, children, null));
    }

    private static FElem wrapCoordinates(final String coordinateStr) {
        return ValidationReport.argument(coordinatesArgument, coordinateStr.getBytes());
    }

    private static String coordinates(@NotNull final org.deegree.geometry.Geometry affectedGeometry) {
        if (affectedGeometry instanceof Curve) {
            final Points points = ((Curve) affectedGeometry).getControlPoints();
            final StringBuilder builder = new StringBuilder(points.size() * points.getDimension() * 12);
            if (points.getDimension() == 3) {
                for (Point point : points) {
                    builder.append(formatValue(point.get0())).append(',')
                            .append(formatValue(point.get1())).append(',')
                            .append(formatValue(point.get2())).append(' ');
                }
            } else {
                for (Point point : points) {
                    builder.append(formatValue(point.get0())).append(',')
                            .append(formatValue(point.get1())).append(' ');
                }
            }
            if (builder.length() > 60) {
                builder.setLength(60);
                builder.append("...");
            } else {
                builder.deleteCharAt(builder.length() - 1);
            }
            return builder.toString();
        } else if (affectedGeometry instanceof Point) {
            return formatPoint(((Point) affectedGeometry));
        } else {
            Objects.requireNonNull(affectedGeometry, "Invalid call, affected points are null");
            throw new IllegalArgumentException(
                    "Cannot derive affected points from geometry type " + affectedGeometry.getClass().getName());
        }
    }

    private static String coordinates(final com.vividsolutions.jts.geom.Geometry geometry, final Coordinate coordProblem) {
        if (coordProblem != null) {
            return formatCoordinate(coordProblem);
        }
        if (geometry != null && geometry.getCoordinates().length > 0) {
            final Coordinate[] coordinates = geometry.getCoordinates();
            final Coordinate coordBegin = coordinates[0];
            final Coordinate coordEnd = coordinates[coordinates.length - 1];
            return formatCoordinate(coordBegin) + " " + formatCoordinate(coordEnd);
        }
        return "";
    }

    public byte getResult() {
        return result;
    }

    public List<FElem> getMessages() {
        return messages;
    }
}
