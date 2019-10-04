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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.vividsolutions.jts.geom.Envelope;

/**
 * ExternalizableJtsEnvelope
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class ExternalizableJtsEnvelope implements Externalizable {
    private double minx;
    private double maxx;
    private double miny;
    private double maxy;

    public ExternalizableJtsEnvelope() {}

    ExternalizableJtsEnvelope(final Envelope envelope) {
        this.minx = envelope.getMinX();
        this.maxx = envelope.getMaxX();
        this.miny = envelope.getMinY();
        this.maxy = envelope.getMaxY();
    }

    public Envelope toEnvelope() {
        return new Envelope(this.minx, this.maxx, this.miny, this.maxy);
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeDouble(this.minx);
        out.writeDouble(this.maxx);
        out.writeDouble(this.miny);
        out.writeDouble(this.maxy);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        this.minx = in.readDouble();
        this.maxx = in.readDouble();
        this.miny = in.readDouble();
        this.maxy = in.readDouble();
    }
}
