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

import java.io.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.io.FileUtils;
import org.basex.query.value.node.ANode;
import org.deegree.commons.xml.stax.XMLStreamReaderWrapper;
import org.deegree.cs.CRSCodeType;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.Geometry;
import org.deegree.gml.GMLInputFactory;
import org.deegree.gml.GMLStreamReader;
import org.deegree.gml.GMLVersion;

import de.interactive_instruments.IFile;
import de.interactive_instruments.IoUtils;
import de.interactive_instruments.etf.bsxm.geometry.IIGeometryFactory;
import de.interactive_instruments.etf.bsxm.parser.BxNamespaceHolder;
import de.interactive_instruments.etf.bsxm.parser.DBNodeStreamReader;
import de.interactive_instruments.properties.PropertyUtils;

/**
 * Transforms Geometry objects into Deegree Geometry objects
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public final class DeegreeTransformer {

    public static final String ETF_GMLGEOX_SRSCONFIG_DIR = "etf.gmlgeox.srsconfig.dir";

    private final IIGeometryFactory geometryFactory;
    private final BxNamespaceHolder namespaceHolder;

    DeegreeTransformer(final IIGeometryFactory deegreeGeomFac, final BxNamespaceHolder namespaceHolder) {
        this.geometryFactory = deegreeGeomFac;
        this.namespaceHolder = namespaceHolder;

        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            if (CRSManager.get("default") == null || CRSManager.get("default")
                    .getCRSByCode(CRSCodeType.valueOf("http://www.opengis.net/def/crs/EPSG/0/5555")) == null) {
                loadGmlGeoXSrsConfiguration();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    private void loadGmlGeoXSrsConfiguration() {
        final String srsConfigDirPath = PropertyUtils.getenvOrProperty(ETF_GMLGEOX_SRSCONFIG_DIR, null);
        final CRSManager crsMgr = new CRSManager();
        /* If the configuration for EPSG 5555 can be accessed, the CRSManger is already configured by the test driver. */
        if (srsConfigDirPath != null) {
            final IFile srsConfigDirectory = new IFile(srsConfigDirPath, ETF_GMLGEOX_SRSCONFIG_DIR);

            try {
                srsConfigDirectory.expectDirIsWritable();
                crsMgr.init(srsConfigDirectory);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Could not load SRS configuration files from directory referenced from GmlGeoX property '"
                                + ETF_GMLGEOX_SRSCONFIG_DIR + "'. Reference is: " + srsConfigDirPath
                                + " Exception message is: " + e.getMessage(),
                        e);
            }
        } else {
            try {
                /* We use the same folder each time an instance of GmlGeoX is created. The configuration files will not be deleted upon exit. That shouldn't be a problem since we always use the same folder. */
                final String tempDirPath = System.getProperty("java.io.tmpdir");
                final File tempDir = new File(tempDirPath, "gmlGeoXSrsConfig");

                if (tempDir.exists()) {
                    FileUtils.deleteQuietly(tempDir);
                }
                tempDir.mkdirs();

                IoUtils.copyResourceToFile(this, "/srsconfig/default.xml", new IFile(tempDir, "default.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/ntv2/beta2007.gsb",
                        new IFile(tempDir, "deegree/d3/config/ntv2/beta2007.gsb"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/parser-files.xml",
                        new IFile(tempDir, "deegree/d3/parser-files.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/crs-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/crs-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/datum-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/datum-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/ellipsoid-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/ellipsoid-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/pm-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/pm-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/projection-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/projection-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/transformation-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/transformation-definitions.xml"));

                crsMgr.init(tempDir);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Exception occurred while extracting the SRS configuration files provided by GmlGeoX to a temporary "
                                + "directory and loading them from there. Exception message is: " + e.getMessage(),
                        e);
            }
        }
    }

    /**
     * Reads a geometry from the given nod.
     *
     * @param crs
     * @param aNode
     *            represents a GML geometry element
     * @return the geometry represented by the node
     * @throws Exception
     */
    public Geometry parseGeometry(final ICRS crs, ANode aNode) throws GmlGeoXException {

        final String namespaceURI = new String(aNode.qname().uri());

        if (namespaceURI == null
                || (!IIGmlConstants.isGML32Namespace(namespaceURI) && !IIGmlConstants.isGML31Namespace(namespaceURI))) {

            throw new GmlGeoXException("Cannot identify GML version from namespace '"
                    + (namespaceURI == null ? "<null>" : namespaceURI) + "'.");
        }

        final GMLVersion gmlVersion;
        if (IIGmlConstants.isGML32Namespace(namespaceURI)) {
            gmlVersion = GMLVersion.GML_32;
        } else if (IIGmlConstants.isGML31Namespace(namespaceURI)) {
            gmlVersion = GMLVersion.GML_31;
        } else {
            // cannot happen because we checked before
            throw new IllegalStateException();
        }

        final XMLStreamReader xmlStream = nodeToStreamReader(aNode);
        try {
            final GMLStreamReader gmlStream = GMLInputFactory.createGMLStreamReader(gmlVersion, xmlStream);
            gmlStream.setGeometryFactory(geometryFactory);
            gmlStream.setDefaultCRS(crs);
            return gmlStream.readGeometry();
        } catch (final XMLStreamException | UnknownCRSException e) {
            throw new GmlGeoXException(e);
        }

    }

    public IIGeometryFactory getGeometryFactory() {
        return geometryFactory;
    }

    XMLStreamReader nodeToStreamReader(final ANode node) {
        final String systemId = new IFile(node.data().meta.original).getName();
        return new XMLStreamReaderWrapper(new DBNodeStreamReader(node, this.namespaceHolder), systemId);
    }
}
