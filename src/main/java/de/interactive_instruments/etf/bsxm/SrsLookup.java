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

import org.basex.query.value.node.ANode;
import org.basex.util.Token;
import org.basex.util.hash.TokenIntMap;
import org.deegree.cs.CRSCodeType;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.persistence.CRSManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import de.interactive_instruments.SUtils;

/**
 * Command to determine the SRS
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 * @author Johannes Echterhoff ( echterhoff aT interactive-instruments doT de )
 */
final public class SrsLookup {
    // Byte comparison
    private static final byte[] srsNameB = "srsName".getBytes();
    private static final byte[] boundedByB = "boundedBy".getBytes();
    private static final byte[] envelopeB = "Envelope".getBytes();
    private String standardSRS = null;
    private ICRS standardDeegreeSRS = null;

    void setStandardSRS(final String standardSRS) {
        if (SUtils.isNullOrEmpty(standardSRS)) {
            this.standardSRS = null;
            this.standardDeegreeSRS = null;
        } else {
            this.standardSRS = standardSRS;
            this.standardDeegreeSRS = CRSManager.get("default").getCRSByCode(CRSCodeType.valueOf(standardSRS));
        }
    }

    @Contract(pure = true)
    String getStandardSRS() {
        return standardSRS;
    }

    @Nullable
    public ICRS getSrsForGeometryNode(final ANode geometryNode) {

        final String srsName = determineSrsName(geometryNode);
        if (srsName != null) {
            return lookup(srsName);
        } else {
            return null;
        }
    }

    @Nullable
    public ICRS getSrsForGeometryComponentNode(final ANode geometryComponentNode) {

        final String srsName = determineSrsNameForGeometryComponent(geometryComponentNode);
        if (srsName != null) {
            return lookup(srsName);
        } else {
            return null;
        }
    }

    public ICRS lookup(String srsName) {

        if (srsName.equals(standardSRS)) {
            // use pre-computed ICRS
            return standardDeegreeSRS;
        } else {
            return CRSManager.getCRSRef(srsName);
        }
    }

    @Nullable
    String determineSrsName(@NotNull final ANode geometryNode) {

        final byte[] srsDirect = geometryNode.attribute(srsNameB);

        // search for @srsName in geometry node first
        if (srsDirect != null) {

            return Token.string(srsDirect);

        } else if (this.standardSRS != null) {

            return this.standardSRS;

        } else {

            /* Check the attribute index. If it does NOT contain an srsName attribute, then a search for such attributes in the wider XML structure (see the following steps) can be avoided. */
            if (geometryNode.data() != null && !attributeIndexHasSrsName(geometryNode)) {
                return null;
            }

            String srsName = searchSrsNameInAncestors(geometryNode);

            if (srsName != null) {
                return srsName;
            } else {
                return searchSrsNameInAncestorBoundedBy(geometryNode);
            }
        }
    }

    private @Nullable String searchSrsNameInAncestorBoundedBy(@NotNull ANode node) {

        // Search in ancestor for boundedBy/Envelope with @srsName
        for (final ANode ancestor : node.ancestorIter()) {
            for (final ANode ancestorChild : ancestor.childIter()) {
                if (Token.eq(boundedByB, Token.local(ancestorChild.name()))) {
                    for (final ANode boundedByChild : ancestorChild.childIter()) {
                        if (Token.eq(envelopeB, Token.local(boundedByChild.name()))) {
                            final byte[] srs = boundedByChild.attribute(srsNameB);
                            if (srs != null) {
                                return Token.string(srs);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String searchSrsNameInAncestors(@NotNull ANode node) {

        // Traverse the ancestor nodes. The following time-consuming steps should be
        // avoided by setting the default srs.
        for (final ANode ancestor : node.ancestorIter()) {
            final byte[] srs = ancestor.attribute(srsNameB);
            if (srs != null) {
                return Token.string(srs);
            }
        }

        return null;
    }

    private boolean attributeIndexHasSrsName(@NotNull ANode node) {

        // query the index (side effect: a non-existing index will be created)
        final int index = node.data().attrNames.index(srsNameB);
        final TokenIntMap values = node.data().attrNames.stats(index).values;
        if (values == null || values.size() == 0) {
            // we will never find an srsName attribute
            return false;
        } else {
            return true;
        }
    }

    @Nullable
    String determineSrsNameForGeometryComponent(@NotNull final ANode geometryComponentNode) {

        if (this.standardSRS != null) {
            return this.standardSRS;
        } else {

            // NOTE: DO NOT search for @srsName in the component itself

            /* Check the attribute index. If it does NOT contain an srsName attribute, then a search for such attributes in the wider XML structure (see the following steps) can be avoided. */
            if (geometryComponentNode.data() != null && !attributeIndexHasSrsName(geometryComponentNode)) {
                return null;
            }

            String srsName = searchSrsNameInAncestors(geometryComponentNode);

            if (srsName != null) {
                return srsName;
            } else {
                return searchSrsNameInAncestorBoundedBy(geometryComponentNode);
            }
        }
    }
}
