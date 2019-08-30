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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.geometry.GeometryFactory;
import org.deegree.geometry.linearization.LinearizationCriterion;
import org.deegree.geometry.linearization.MaxErrorCriterion;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.segments.CurveSegment;

/** Based on the implementation of {@link GeometryFactory}. */
public class IIGeometryFactory extends GeometryFactory implements Externalizable {

    protected double maxError = 0.00001;
    protected int maxNumPoints = 1000;

    @Override
    public Curve createCurve(final String id, final ICRS crs, final CurveSegment... segments) {
        return (Curve) inspect(new IICurve(id, crs, pm, Arrays.asList(segments), this));
    }

    /**
     * @param maxError
     *            the maxError to set
     */
    public void setMaxError(double maxError) {
        this.maxError = maxError;
    }

    /**
     * @param maxNumPoints
     *            the maxNumPoints to set
     */
    public void setMaxNumPoints(int maxNumPoints) {
        this.maxNumPoints = maxNumPoints;
    }

    public LinearizationCriterion getMaxErrorCriterion() {

        return new MaxErrorCriterion(maxError, maxNumPoints);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {

        /* Thus far we are not concerned with fields from supertypes - the default values of these fields suffice. */

        out.writeDouble(maxError);
        out.writeInt(maxNumPoints);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        /* Thus far we are not concerned with fields from supertypes - the default values of these fields suffice. */

        this.maxError = in.readDouble();
        this.maxNumPoints = in.readInt();
    }
}
