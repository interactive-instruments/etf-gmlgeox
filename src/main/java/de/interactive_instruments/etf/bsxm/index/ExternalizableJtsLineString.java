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
package de.interactive_instruments.etf.bsxm.index;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;

/**
 * ExternalizableJtsLineString
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class ExternalizableJtsLineString implements ExternalizableJtsGeometry {

    private double[] coordinates;

    public ExternalizableJtsLineString() {}

    ExternalizableJtsLineString(final LineString lineString) {
        coordinates = ExternalizableJtsGeometry.simplify(lineString.getCoordinateSequence());
    }

    @Override
    public Geometry toJtsGeometry(final GeometryFactory factory) {
        return new LineString(new CoordinateArraySequence(ExternalizableJtsGeometry.toCoordinateArray(this.coordinates)),
                factory);
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeInt(coordinates.length);
        for (int i = 0; i < coordinates.length; i++) {
            out.writeDouble(coordinates[i]);
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        coordinates = new double[in.readInt()];
        for (int i = 0; i < coordinates.length; i++) {
            coordinates[i] = in.readDouble();
        }
    }
}
