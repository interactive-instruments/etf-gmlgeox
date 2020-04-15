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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.geometry.Rectangle;

/**
 * ExternalizableRtreeGeometry
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
interface ExternalizableRtreeGeometry extends Externalizable {

    class ExternalizableRtree2DPoint implements ExternalizableRtreeGeometry {

        private float x;
        private float y;

        @Override
        public Point toRtreeGeometry() {
            return Geometries.point(x, y);
        }

        ExternalizableRtree2DPoint() {}

        ExternalizableRtree2DPoint(final Point point) {
            this.x = point.x();
            this.y = point.y();
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeFloat(x);
            out.writeFloat(y);
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            x = in.readFloat();
            y = in.readFloat();
        }
    }

    class ExternalizableRtreeRectangle implements ExternalizableRtreeGeometry {

        private float x1;
        private float y1;
        private float x2;
        private float y2;

        public ExternalizableRtreeRectangle() {}

        ExternalizableRtreeRectangle(final Rectangle geometry) {
            x1 = geometry.x1();
            y1 = geometry.y1();
            x2 = geometry.x2();
            y2 = geometry.y2();
        }

        @Override
        public Rectangle toRtreeGeometry() {
            return Geometries.rectangle(x1, y1, x2, y2);
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeFloat(x1);
            out.writeFloat(y1);
            out.writeFloat(x2);
            out.writeFloat(y2);
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            x1 = in.readFloat();
            y1 = in.readFloat();
            x2 = in.readFloat();
            y2 = in.readFloat();
        }
    }

    Geometry toRtreeGeometry();

    static ExternalizableRtreeGeometry create(final Geometry geometry) {
        if (geometry instanceof Rectangle) {
            return new ExternalizableRtreeRectangle((Rectangle) geometry);
        } else {
            return new ExternalizableRtree2DPoint((Point) geometry);
        }
    }

}
