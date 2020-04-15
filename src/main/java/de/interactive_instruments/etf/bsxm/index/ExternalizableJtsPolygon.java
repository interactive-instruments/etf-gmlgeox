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
package de.interactive_instruments.etf.bsxm.index;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.*;

/**
 * ExternalizableJtsPolygon
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class ExternalizableJtsPolygon implements ExternalizableJtsGeometry {

    private double[] shell;
    private List<double[]> holesList;

    public ExternalizableJtsPolygon() {}

    ExternalizableJtsPolygon(final Polygon polygon) {
        shell = ExternalizableJtsGeometry.simplify(polygon.getExteriorRing().getCoordinateSequence());
        if (polygon.getNumInteriorRing() != 0) {
            holesList = new ArrayList<>(polygon.getNumInteriorRing());
            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                holesList.add(ExternalizableJtsGeometry.simplify(polygon.getInteriorRingN(i).getCoordinateSequence()));
            }
        }
    }

    @Override
    public Geometry toJtsGeometry(final GeometryFactory factory) {
        final LinearRing shellLinearRing = ExternalizableJtsGeometry.toLinearRing(shell, factory);
        if (holesList == null) {
            return new Polygon(shellLinearRing, null, factory);
        } else {
            final LinearRing[] holes = new LinearRing[holesList.size()];
            for (int i = 0; i < holesList.size(); i++) {
                holes[i] = ExternalizableJtsGeometry.toLinearRing(holesList.get(i), factory);
            }
            return new Polygon(shellLinearRing, holes, factory);
        }
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeInt(shell.length);
        for (int i = 0; i < shell.length; i++) {
            out.writeDouble(shell[i]);
        }
        if (holesList == null) {
            out.writeInt(0);
        } else {
            out.writeInt(holesList.size());
            for (final double[] holes : holesList) {
                out.writeInt(holes.length);
                for (int i = 0; i < holes.length; i++) {
                    out.writeDouble(holes[i]);
                }
            }
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        shell = new double[in.readInt()];
        for (int i = 0; i < shell.length; i++) {
            shell[i] = in.readDouble();
        }
        final int holeListSize = in.readInt();
        if (holeListSize != 0) {
            holesList = new ArrayList<>(holeListSize);
            for (int i = 0; i < holeListSize; i++) {
                final double[] holes = new double[in.readInt()];
                for (int p = 0; p < holes.length; p++) {
                    holes[p] = in.readDouble();
                }
                holesList.add(holes);
            }
        }
    }
}
