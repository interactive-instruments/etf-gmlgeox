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

import java.util.Collections;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.dom.DOMSource;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.exc.WstxParsingException;

import org.codehaus.stax2.ri.dom.DOMWrappingReader;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

/**
 * BaseX ANode wrapper with better performance
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class BsxDomWrappingReader extends DOMWrappingReader {

    private final ReaderConfig cfg;

    protected BsxDomWrappingReader(final DOMSource src, ReaderConfig cfg) throws XMLStreamException {
        super(src, cfg.willSupportNamespaces(), cfg.willCoalesceText());
        // [WSTX-162]: allow enabling/disabling name/ns intern()ing
        if (cfg.hasInternNamesBeenEnabled()) {
            setInternNames(true);
        }
        if (cfg.hasInternNsURIsBeenEnabled()) {
            setInternNsURIs(true);
        }
        this.cfg = cfg;
    }

    public boolean isPropertySupported(String name) {
        return cfg.isPropertySupported(name);
    }

    public Object getProperty(String name) {
        if (name.equals("javax.xml.stream.entities")) {
            return Collections.EMPTY_LIST;
        }
        if (name.equals("javax.xml.stream.notations")) {
            return Collections.EMPTY_LIST;
        }
        return cfg.getProperty(name);
    }

    public boolean setProperty(String name, Object value) {
        return cfg.setProperty(name, value);
    }

    @Override
    protected void throwStreamException(String msg, Location loc)
            throws XMLStreamException {
        if (loc == null) {
            throw new WstxParsingException(msg);
        }
        throw new WstxParsingException(msg, loc);
    }

    public String getAttributeValue(String nsURI, String localName) {
        if (_currEvent != START_ELEMENT) {
            reportWrongState(ERR_STATE_NOT_START_ELEM);
        }
        final Element elem = (Element) _currNode;
        final NamedNodeMap attrs = elem.getAttributes();
        if (nsURI != null && nsURI.length() == 0) {
            nsURI = null;
        } else {
            nsURI = nsURI + ":";
        }
        final Attr attr = (Attr) attrs.getNamedItem(nsURI + localName);
        return (attr == null) ? null : attr.getValue();
    }

    public static XMLStreamReader createFrom(final DOMSource domSource, final ReaderConfig config) throws XMLStreamException {
        return new BsxDomWrappingReader(domSource, config);
    }
}
