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
package de.interactive_instruments.etf.bsxm.parser;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import de.interactive_instruments.SUtils;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class GmlId {

    private GmlId() {}

    public static class ElementAndId {
        public final Element element;
        public final String id;

        private ElementAndId(final Element element, final String id) {
            this.element = element;
            this.id = id;
        }
    }

    private static String getAnyId(final Element current) {
        final NamedNodeMap attributes = current.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Node attrib = attributes.item(i);
            if (attrib.getLocalName().equals("id")) {
                return attrib.getNodeValue();
            }
        }
        return null;
    }

    public static String getId(final Element current) {
        final String id = getAnyId(current);
        if (id != null) {
            return id;
        } else if (current.getParentNode() != null) {
            return getId((Element) current.getParentNode());
        } else {
            return null;
        }
    }

    public static String getId(final org.deegree.geometry.Geometry geom, final Element current) {
        final String geomId = geom.getId();
        if (!SUtils.isNullOrEmpty(geomId)) {
            return geomId;
        }
        return getId(current);
    }

    private static ElementAndId getAnyElementWithId(final Element current) {
        final NamedNodeMap attributes = current.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Node attrib = attributes.item(i);
            if (attrib.getLocalName().equals("id")) {
                return new ElementAndId(current, attrib.getNodeValue());
            }
        }
        return null;
    }

    public static ElementAndId getElementWithId(final Element current) {
        final ElementAndId elementAndId = getAnyElementWithId(current);
        if (elementAndId != null) {
            return elementAndId;
        } else if (current.getParentNode() != null) {
            return getElementWithId((Element) current.getParentNode());
        } else {
            return null;
        }
    }

}
