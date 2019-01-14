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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessage;
import nl.vrom.roo.validator.core.dom4j.handlers.GeometryElementHandler;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.internal.EntryDefault;
import com.google.common.base.Joiner;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.util.GeometryExtracter;
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.basex.api.dom.BXNode;
import org.basex.core.Context;
import org.basex.data.Data;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.QueryProcessor;
import org.basex.query.value.Value;
import org.basex.query.value.item.Atm;
import org.basex.query.value.item.Item;
import org.basex.query.value.item.Jav;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;
import org.basex.query.value.seq.Empty;
import org.basex.util.InputInfo;
import org.deegree.commons.xml.XMLParsingException;
import org.deegree.cs.CRSCodeType;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.Geometry;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.interactive_instruments.IFile;
import de.interactive_instruments.IoUtils;
import de.interactive_instruments.properties.PropertyUtils;

/**
 * This module supports the validation of geometries as well as computing the spatial relationship between geometries.
 * <p>
 * NOTE 1: the validation and spatial relationship methods only support specific sets of geometry types - please see the documentation of the respective methods for details on which geometry types are supported.
 * </p>
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class GmlGeoX extends QueryModule {

    public static final String NS = "de.interactive_instruments.etf.bsxm.GmlGeoX";
    public static final String PREFIX = "ggeo";

    public static final String ETF_GMLGEOX_SRSCONFIG_DIR = "etf.gmlgeox.srsconfig.dir";

    public enum SpatialRelOp {
        CONTAINS, CROSSES, EQUALS, INTERSECTS, ISDISJOINT, ISWITHIN, OVERLAPS, TOUCHES
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GmlGeoX.class);

    private static final Pattern INTERSECTIONPATTERN = Pattern.compile("[0-2\\*TF]{9}");

    private static final String SRS_SEARCH_QUERY = "declare namespace xlink='http://www.w3.org/1999/xlink';"
            + " declare variable $geom external; let $adv := root($geom)/*[local-name() = ('AX_Bestandsdatenauszug','AX_NutzerbezogeneBestandsdatenaktualisierung_NBA','AA_Fortfuehrungsauftrag','AX_Einrichtungsauftrag')]"
            + " return if($adv) then $adv/*:koordinatenangaben/*[xs:boolean(*:standard)]/*:crs/data(@xlink:href)"
            + " else let $ancestors := $geom/ancestor::* let $ancestorWithSrsName := $ancestors[@srsName][1]"
            + " return if($ancestorWithSrsName) then $ancestorWithSrsName/data(@srsName)"
            + " else $ancestors[*:boundedBy/*:Envelope/@srsName][1]/*:boundedBy/*:Envelope/data(@srsName)";

    // Byte comparison
    private static final byte[] srsNameB = new String("srsName").getBytes();

    protected final GmlGeoXUtils geoutils = new GmlGeoXUtils(this);

    private final Set<String> gmlGeometries = new TreeSet<String>();

    private String standardSRS = null;

    private static final boolean debug = LOGGER.isDebugEnabled();

    private GeometryManager mgr = new GeometryManager();

    private Map<String, List<Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>>> geomIndexEntriesByIndexName = new HashMap<>();

    private int count = 0;
    private int count2 = 0;

    public GmlGeoX() throws QueryException {

        logMemUsage("GmlGeoX#init");

        // default geometry types for which validation is performed
        registerGmlGeometry("Point");
        registerGmlGeometry("Polygon");
        registerGmlGeometry("Surface");
        registerGmlGeometry("Curve");
        registerGmlGeometry("LinearRing");

        registerGmlGeometry("MultiPoint");
        registerGmlGeometry("MultiPolygon");
        registerGmlGeometry("MultiGeometry");
        registerGmlGeometry("MultiSurface");
        registerGmlGeometry("MultiCurve");

        registerGmlGeometry("Ring");
        registerGmlGeometry("LineString");

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

    private void loadGmlGeoXSrsConfiguration() throws QueryException {
        final String srsConfigDirPath = PropertyUtils.getenvOrProperty(ETF_GMLGEOX_SRSCONFIG_DIR, null);
        final CRSManager crsMgr = new CRSManager();
        /* If the configuration for EPSG 5555 can be accessed, the CRSManger is already configured by the test driver. */
        if (srsConfigDirPath != null) {
            final IFile srsConfigDirectory = new IFile(srsConfigDirPath, ETF_GMLGEOX_SRSCONFIG_DIR);

            try {
                srsConfigDirectory.expectDirIsWritable();
                crsMgr.init(srsConfigDirectory);
            } catch (Exception e) {
                throw new QueryException(
                        "Could not load SRS configuration files from directory referenced from GmlGeoX property '"
                                + ETF_GMLGEOX_SRSCONFIG_DIR + "'. Reference is: " + srsConfigDirPath
                                + " Exception message is: " + e.getMessage());
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
                throw new QueryException(
                        "Exception occurred while extracting the SRS configuration files provided by GmlGeoX to a temporary "
                                + "directory and loading them from there. Exception message is: " + e.getMessage());
            }
        }
    }

    /**
     * Loads SRS configuration files from the given directory, to be used when looking up SRS names for creating geometry objects.
     *
     * @param configurationDirectoryPathName
     *            Path to a directory that contains SRS configuration files
     * @throws QueryException
     *             in case that the SRS configuration directory does not exist, is not a directory, cannot be read, or an exception occurred while loading the configuration files
     */
    @Requires(Permission.NONE)
    public void configureSpatialReferenceSystems(final String configurationDirectoryPathName) throws QueryException {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            final File configurationDirectory = new File(configurationDirectoryPathName);
            if (!configurationDirectory.exists() || !configurationDirectory.isDirectory()
                    || !configurationDirectory.canRead()) {
                throw new IllegalArgumentException(
                        "Given path name does not identify a directory that exists and that can be read.");
            } else {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                CRSManager crsMgr = new CRSManager();
                crsMgr.init(configurationDirectory);
            }

        } catch (Exception e) {
            throw new QueryException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    /**
     * Retrieve the Well-Known-Text representation of a given JTS geometry.
     *
     * @param geom
     * @return the WKT representation of the given geometry, or '&lt;null&gt;' if the geometry is <code>null</code>.
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String toWKT(com.vividsolutions.jts.geom.Geometry geom) {
        if (geom == null) {
            return "<null>";
        } else {
            return geom.toText();
        }
    }

    /**
     * Flattens the given geometry if it is a geometry collection. Members that are not geometry collections are added to the result. Thus, MultiPoint, -LineString, and -Polygon will also be flattened. Contained geometry collections are recursively scanned for relevant members.
     *
     * @param geom
     *            a JTS geometry, can be a JTS GeometryCollection
     * @return a sequence of JTS geometry objects that are not collection types
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry[] flattenAllGeometryCollections(
            com.vividsolutions.jts.geom.Geometry geom) {

        if (geom == null) {

            return new com.vividsolutions.jts.geom.Geometry[]{};

        } else {

            final List<com.vividsolutions.jts.geom.Geometry> geoms;

            if (geom instanceof GeometryCollection) {

                GeometryCollection col = (GeometryCollection) geom;

                geoms = new ArrayList<>(col.getNumGeometries());

                for (int i = 0; i < col.getNumGeometries(); i++) {
                    geoms.addAll(Arrays.asList(flattenAllGeometryCollections(col.getGeometryN(i))));
                }

            } else {
                geoms = Collections.singletonList(geom);
            }

            return geoms.toArray(new com.vividsolutions.jts.geom.Geometry[geoms.size()]);
        }
    }

    /**
     * Calls the {@link #validate(ANode, String)} method, with <code>null</code> as the bitmask, resulting in a validation with all tests enabled.
     * <p>
     * See the documentation of the {@link #validate(ANode, String)} method for a description of the supported geometry types.
     *
     * @param node
     *            Node
     * @return a mask with the test results, encoded as characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVF' shows that the first test was skipped, while the second test passed and the third failed.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public String validate(ANode node) throws QueryException {
        return this.validate(node, null);
    }

    /**
     * Validates the given (GML geometry) node.
     * <p>
     * By default validation is only performed for the following GML geometry elements: Point, Polygon, Surface, Curve, LinearRing, MultiPolygon, MultiGeometry, MultiSurface, MultiCurve, Ring, and LineString. The set of GML elements to validate can be modified via the following methods: {@link #registerGmlGeometry(String)}, {@link #unregisterGmlGeometry(String)}, and {@link #unregisterAllGmlGeometries()}. These methods are also available for XQueries.
     * <p>
     * The validation tasks to perform can be specified via the given mask. The mask is a simple string, where the character '1' at the position of a specific test (assuming a 1-based index) specifies that the test shall be performed. If the mask does not contain a character at the position of a specific test (because the mask is empty or the length is smaller than the position), then the test will be executed.
     * <p>
     * The following tests are available:
     * <p>
     * <table summary="Available tests">
     * <tr>
     * <th>Position</th>
     * <th>Test Name</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td>General Validation</td>
     * <td>This test validates the given geometry using the validation functionality of both deegree and JTS.</td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td>Polygon Patch Connectivity</td>
     * <td>Checks that multiple polygon patches within a single surface are connected.</td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td>Repetition of Position in CurveSegments</td>
     * <td>Checks that consecutive positions within a CurveSegment are not equal.</td>
     * </tr>
     * </table>
     * <p>
     * Examples:
     * <ul>
     * <li>The mask '010' indicates that only the 'Polygon Patch Connectivity' test shall be performed.</li>
     * <li>The mask '1' indicates that all tests shall be performed (because the first one is set to true/'1' and nothing is said for the other tests).</li>
     * <li>The mask '0' indicates that all except the first test shall be performed.
     * </ul>
     *
     * @param node
     *            the GML geometry to validate
     * @param testMask
     *            test mask
     * @return a mask with the test results, encoded as characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVF' shows that the first test was skipped, while the second test passed and the third failed.
     * @throws QueryException
     */
    public String validate(ANode node, String testMask) throws QueryException {
        ValidationReport vr = this.executeValidate(node, testMask);
        return vr.getValidationResult();
    }

    public Element validateAndReport(ANode node) throws QueryException {
        return validateAndReport(node, null);
    }

    /**
     * @see #executeValidate(ANode, String)
     * @param node
     *            Node
     * @param testMask
     *            test mask
     * @return a DOM element like the following:
     *
     *         <pre>
     *         {@code
     *         <ggeo:ValidationResult xmlns:ggeo=
    "de.interactive_instruments.etf.bsxm.GmlGeoX">
     *           <ggeo:isValid>false</ggeo:isValid>
     *           <ggeo:result>VFV</ggeo:result>
     *           <ggeo:message type=
    "NOTICE">Detected GML standard version: GML3.2.</ggeo:message>
     *           <ggeo:message type=
    "ERROR">Invalid surface (gml:id: s14). The patches of the surface are not connected.</ggeo:message>
     *         </ggeo:ValidationResult>}
     *         </pre>
     *
     *         Where:
     *         <ul>
     *         <li>ggeo:isValid - contains the boolean value indicating if the object passed all tests (defined by the testMask).</li>
     *         <li>ggeo:result - contains a string that is a mask with the test results, encoded as characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVF' shows that the first test was skipped, while the second test passed and the third failed</li>
     *         <li>ggeo:message (one for each message produced during validation) contains:
     *         <ul>
     *         <li>an XML attribute 'type' that indicates the severity level of the message ('FATAL', 'ERROR', 'WARNING', or 'NOTICE')</li>
     *         <li>the actual validation message as text content</li>
     *         </ul>
     *         </ul>
     * @throws QueryException
     */
    public Element validateAndReport(ANode node, String testMask) throws QueryException {

        ValidationReport vr = this.executeValidate(node, testMask);

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setNamespaceAware(true);

        DocumentBuilder docBuilder;

        try {
            docBuilder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new QueryException(e);
        }

        // root elements
        Document doc = docBuilder.newDocument();

        Element root = doc.createElementNS(NS, PREFIX + ":ValidationResult");
        doc.appendChild(root);

        Element isValid = doc.createElementNS(NS, PREFIX + ":isValid");
        isValid.setTextContent(vr.isValid() ? "true" : "false");

        root.appendChild(isValid);

        Element result = doc.createElementNS(NS, PREFIX + ":result");
        result.setTextContent(vr.getValidationResult());

        root.appendChild(result);

        for (ValidatorMessage vm : vr.getValidatorMessages()) {

            Element msg = doc.createElementNS(NS, PREFIX + ":message");
            root.appendChild(msg);

            msg.setAttribute("type", vm.getType().toString());
            msg.setTextContent(vm.getMessage());
        }

        return root;
    }

    /**
     * Validates the given (GML geometry) node.
     * <p>
     * By default validation is only performed for the following GML geometry elements: Point, Polygon, Surface, Curve, LinearRing, MultiPolygon, MultiGeometry, MultiSurface, MultiCurve, Ring, and LineString. The set of GML elements to validate can be modified via the following methods: {@link #registerGmlGeometry(String)}, {@link #unregisterGmlGeometry(String)}, and {@link #unregisterAllGmlGeometries()}. These methods are also available for XQueries.
     * <p>
     * The validation tasks to perform can be specified via the given mask. The mask is a simple string, where the character '1' at the position of a specific test (assuming a 1-based index) specifies that the test shall be performed. If the mask does not contain a character at the position of a specific test (because the mask is empty or the length is smaller than the position), then the test will be executed.
     * <p>
     * The following tests are available:
     * <p>
     * <table>
     * <tr>
     * <th>Position</th>
     * <th>Test Name</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td>General Validation</td>
     * <td>This test validates the given geometry using the validation functionality of both deegree and JTS.</td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td>Polygon Patch Connectivity</td>
     * <td>Checks that multiple polygon patches within a single surface are connected.</td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td>Repetition of Position in CurveSegments</td>
     * <td>Checks that consecutive positions within a CurveSegment are not equal.</td>
     * </tr>
     * </table>
     * <p>
     * Examples:
     * <ul>
     * <li>The mask '010' indicates that only the 'Polygon Patch Connectivity' test shall be performed.</li>
     * <li>The mask '1' indicates that all tests shall be performed (because the first one is set to true/'1' and nothing is said for the other tests).</li>
     * <li>The mask '0' indicates that all except the first test shall be performed.
     * </ul>
     *
     * @param node
     *            the GML geometry to validate
     * @return a validation report, with the validation result and validation message (providing further details about any errors). The validation result is encoded as a sequence of characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVF' shows that the first test was skipped, while the second test passed and the third failed.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    ValidationReport executeValidate(ANode node, String testMask) throws QueryException {

        try {

            // determine which tests to execute
            boolean isTestGeonovum, isTestPolygonPatchConnectivity, isTestRepetitionInCurveSegments;

            if (testMask == null) {

                isTestGeonovum = true;
                isTestPolygonPatchConnectivity = true;
                isTestRepetitionInCurveSegments = true;

            } else {
                isTestGeonovum = testMask.length() >= 1 && testMask.charAt(0) == '1';
                isTestPolygonPatchConnectivity = testMask.length() >= 2 && testMask.charAt(1) == '1';
                isTestRepetitionInCurveSegments = testMask.length() >= 3 && testMask.charAt(2) == '1';
            }

            boolean isValidGeonovum = false;
            boolean polygonPatchesAreConnected = false;
            boolean noRepetitionInCurveSegment = false;

            String srsName = determineSrsName(node);

            BXNode elem = node.toJava();

            List<ValidatorMessage> validationMessages = new ArrayList<ValidatorMessage>();
            // ================
            // Geonovum validation (deegree and JTS validation)

            if (isTestGeonovum) {

                ValidatorContext ctx = new ValidatorContext();

                GeometryElementHandler handler = new GeometryElementHandler(ctx, null, srsName);
                /* configure handler with GML geometries specified through this class */
                handler.unregisterAllGmlGeometries();
                for (String additionalGmlElementName : gmlGeometries) {
                    handler.registerGmlGeometry(additionalGmlElementName);
                }

                SAXReader saxReader = new SAXReader();
                saxReader.setDefaultHandler(handler);

                final InputStream stream = geoutils.nodeToInputStream(elem);
                saxReader.read(stream);

                isValidGeonovum = ctx.isSuccessful();

                if (!isValidGeonovum) {
                    validationMessages.addAll(ctx.getMessages());
                }
            }

            if (isTestPolygonPatchConnectivity || isTestRepetitionInCurveSegments) {

                ValidatorContext ctx = new ValidatorContext();
                SecondaryGeometryElementValidationHandler handler = new SecondaryGeometryElementValidationHandler(
                        isTestPolygonPatchConnectivity, isTestRepetitionInCurveSegments, ctx, srsName, this);

                /* configure handler with GML geometries specified through this class */
                handler.unregisterAllGmlGeometries();
                for (String additionalGmlElementName : gmlGeometries) {
                    handler.registerGmlGeometry(additionalGmlElementName);
                }

                SAXReader saxReader = new SAXReader();
                saxReader.setDefaultHandler(handler);

                final InputStream stream = geoutils.nodeToInputStream(elem);
                saxReader.read(stream);

                // ================
                // Test: polygon patches of a surface are connected
                if (isTestPolygonPatchConnectivity) {
                    polygonPatchesAreConnected = handler.arePolygonPatchesConnected();
                }

                // ================
                // Test: point repetition in curve segment
                if (isTestRepetitionInCurveSegments) {
                    noRepetitionInCurveSegment = handler.isNoRepetitionInCurveSegments();
                }

                if (!polygonPatchesAreConnected || !noRepetitionInCurveSegment) {
                    validationMessages.addAll(ctx.getMessages());
                }
            }

            // combine results
            StringBuilder sb = new StringBuilder();

            if (!isTestGeonovum) {
                sb.append("S");
            } else if (isValidGeonovum) {
                sb.append("V");
            } else {
                sb.append("F");
            }

            if (!isTestPolygonPatchConnectivity) {
                sb.append("S");
            } else if (polygonPatchesAreConnected) {
                sb.append("V");
            } else {
                sb.append("F");
            }

            if (!isTestRepetitionInCurveSegments) {
                sb.append("S");
            } else if (noRepetitionInCurveSegment) {
                sb.append("V");
            } else {
                sb.append("F");
            }

            return new ValidationReport(sb.toString(), validationMessages);

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    // /**
    // * Tests if the first geometry contains the second geometry.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents the second geometry, encoded as a GML geometry
    // * element
    // * @return <code>true</code> if the first geometry contains the second
    // one,
    // * else <code>false</code>.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean contains(Value arg1, Value arg2) throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.CONTAINS);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing contains(Value,Value). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }

    // /**
    // * Tests if one geometry contains a list of geometries. Whether a match is
    // * required for all or just one of these is controlled via parameter.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents a list of geometries, encoded as a GML geometry
    // * element
    // * @param matchAll
    // * <code>true</code> if arg1 must fulfill the spatial
    // * relationship for all geometries in arg2, else
    // * <code>false</code>
    // * @return <code>true</code> if the conditions are met, else
    // * <code>false</code>. <code>false</code> will also be returned if
    // * arg2 is empty.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean contains(Object arg1, Object arg2, boolean matchAll)
    // throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.CONTAINS, matchAll);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing contains(Object,Object,boolean). Message is: "
    // + e.getMessage());
    //
    // throw qe;
    // }
    // }

    /**
     * Tests if the first geometry contains the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry contains the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean contains(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CONTAINS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing contains(Value,Value). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry contains a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean contains(Value arg1, Value arg2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.CONTAINS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing contains(Value,Value,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry contains the second geometry.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @return <code>true</code> if the first geometry contains the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean containsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CONTAINS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing containsGeomGeom(Geometry,Geometry). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry contains a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean containsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CONTAINS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing containsGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    // /**
    // * Tests if the first geometry crosses the second geometry.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents the second geometry, encoded as a GML geometry
    // * element
    // * @return <code>true</code> if the first geometry crosses the second one,
    // * else <code>false</code>.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean crosses(Value arg1, Value arg2) throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.CROSSES);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing crosses(Value,Value). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }
    //
    // /**
    // * Tests if one geometry crosses a list of geometries. Whether a match is
    // * required for all or just one of these is controlled via parameter.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents a list of geometries, encoded as a GML geometry
    // * element
    // * @param matchAll
    // * <code>true</code> if arg1 must fulfill the spatial
    // * relationship for all geometries in arg2, else
    // * <code>false</code>
    // * @return <code>true</code> if the conditions are met, else
    // * <code>false</code>. <code>false</code> will also be returned if
    // * arg2 is empty.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean crosses(Object arg1, Object arg2, boolean matchAll) throws
    // QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.CROSSES, matchAll);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing crosses(Object,Object,boolean). Message is: "
    // + e.getMessage());
    //
    // throw qe;
    // }
    // }

    /**
     * Tests if the first geometry crosses the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry crosses the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean crosses(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CROSSES);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing crosses(Value,Value). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry crosses a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean crosses(Value arg1, Value arg2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.CROSSES, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing crosses(Value,Value,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry crosses the second geometry.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @return <code>true</code> if the first geometry crosses the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean crossesGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CROSSES);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing crossesGeomGeom(Geometry,Geometry). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry crosses a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean crossesGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CROSSES, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing crossesGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    // /**
    // * Tests if the first geometry equals the second geometry.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents the second geometry, encoded as a GML geometry
    // * element
    // * @return <code>true</code> if the first geometry equals the second one,
    // * else <code>false</code>.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean equals(Value arg1, Value arg2) throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.EQUALS);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing equals(Value,Value). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }

    // /**
    // * Tests if one geometry equals a list of geometries. Whether a match is
    // * required for all or just one of these is controlled via parameter.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents a list of geometries, encoded as a GML geometry
    // * element
    // * @param matchAll
    // * <code>true</code> if arg1 must fulfill the spatial
    // * relationship for all geometries in arg2, else
    // * <code>false</code>
    // * @return <code>true</code> if the conditions are met, else
    // * <code>false</code>. <code>false</code> will also be returned if
    // * arg2 is empty.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean equals(Object arg1, Object arg2, boolean matchAll) throws
    // QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.EQUALS,
    // matchAll);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing equals(Object,Object,boolean). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }

    /**
     * Tests if the first geometry equals the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry equals the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean equals(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.EQUALS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing equals(Value,Value). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry equals a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean equals(Value arg1, Value arg2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.EQUALS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing equals(Value,Value,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry equals the second geometry.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @return <code>true</code> if the first geometry equals the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean equalsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.EQUALS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing equalsGeomGeom(Geometry,Geometry). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry equals a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean equalsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.EQUALS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing equalsGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry intersects the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry intersects the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersects(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.INTERSECTS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing intersects(Value,Value). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry intersects a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersects(Value arg1, Value arg2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.INTERSECTS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing intersects(Value,Value,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry intersects the second geometry.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @return <code>true</code> if the first geometry intersects the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersectsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.INTERSECTS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing intersectsGeomGeom(Geometry,Geometry). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry intersects a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersectsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.INTERSECTS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing intersectsGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Determine the name of the SRS that applies to the given geometry element. The SRS is looked up as follows (in order):
     *
     * <ol>
     * <li>If the element itself has an 'srsName' attribute, then the value of that attribute is returned.</li>
     * <li>Otherwise, if a standard SRS is defined (see {@link #setStandardSRS(String)}), it is used.</li>
     * <li>Otherwise, if the root element of the document of the given element has local name 'AX_Bestandsdatenauszug', 'AX_NutzerbezogeneBestandsdatenaktualisierung_NBA', 'AA_Fortfuehrungsauftrag', or 'AX_Einrichtungsauftrag', then the standard CRS (within element 'koordinatenangaben') as defined by the AAA GeoInfoDok is used.</li>
     * <li>Otherwise, if an ancestor of the given element has the 'srsName' attribute, then the value of that attribute from the first ancestor (i.e., the nearest) is used.</li>
     * <li>Otherwise, if an ancestor of the given element has a child element with local name 'boundedBy', which itself has a child with local name 'Envelope', and that child has an 'srsName' attribute, then the value of that attribute from the first ancestor (i.e., the nearest) that fulfills the criteria is used.</li>
     * </ol>
     *
     * NOTE: The underlying query is independent of a specific GML namespace.
     *
     * @param geometryNode
     *            a gml geometry node
     * @return the value of the applicable 'srsName' attribute, if found, otherwise <code>null</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String determineSrsName(final ANode geometryNode) throws QueryException {

        byte[] srsDirect = geometryNode.attribute(srsNameB);

        if (srsDirect != null) {

            return new String(srsDirect);

        } else if (this.standardSRS != null) {

            return this.standardSRS;

        } else {

            Context ctx = queryContext.context;

            try (QueryProcessor qp = new QueryProcessor(SRS_SEARCH_QUERY, ctx)) {
                // Bind to context:
                qp.bind("geom", geometryNode);
                qp.context(geometryNode);
                Value value = qp.value();

                if (value instanceof Empty) {
                    return null;
                } else if (value instanceof Atm) {
                    return ((Atm) value).toJava();
                } else {
                    // TBD if this case can occur when searching for the SRS
                    return null;
                }
            }
        }
    }

    /**
     * Parse a geometry.
     *
     * @param v
     *            - either a geometry node or a JTS geometry
     * @return a JTS geometry
     * @throws QueryException
     */
    public com.vividsolutions.jts.geom.Geometry parseGeometry(Value v) throws QueryException {

        try {
            if (v instanceof ANode) {

                ANode node = (ANode) v;

                return geoutils.toJTSGeometry(node);

            } else if (v instanceof Jav && ((Jav) v).toJava() instanceof com.vividsolutions.jts.geom.Geometry) {

                return (com.vividsolutions.jts.geom.Geometry) ((Jav) v).toJava();

            } else {
                throw new IllegalArgumentException("First argument is neither a single node nor a JTS geometry.");
            }
        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Compares two objects that represent geometries. If one of these objects is an instance of BaseX Empty, <code>false</code> will be returned. Cannot compare JTS GeometryCollection objects that are real collections (so not one of the subtypes: MultiPoint, MultiLineString, MultiPolygon) - unless both are empty (then the result is <code>false</code>).
     *
     * @param geom1x
     * @param geom2x
     * @param op
     * @return
     * @throws QueryException
     */
    private boolean applySpatialRelationshipOperation(Object geom1x, Object geom2x, SpatialRelOp op)
            throws QueryException {

        if (geom1x == null || geom2x == null || geom1x instanceof Empty || geom2x instanceof Empty) {
            return false;
        }

        if ((geom1x instanceof GeometryCollection
                && geoutils.isGeometryCollectionButNotASubtype((GeometryCollection) geom1x))

                || (geom2x instanceof GeometryCollection
                        && geoutils.isGeometryCollectionButNotASubtype((GeometryCollection) geom2x))) {

            if (geom1x instanceof GeometryCollection && !((GeometryCollection) geom1x).isEmpty()) {
                throw new QueryException(
                        "First argument is a non-empty geometry collection. This is not supported by this method.");
            } else if (geom2x instanceof GeometryCollection && !((GeometryCollection) geom2x).isEmpty()) {
                throw new QueryException(
                        "Second argument is a non-empty geometry collection. This is not supported by this method.");
            }

            return false;
        }

        try {

            DBNode geom1db = null;
            DBNode geom2db = null;

            com.vividsolutions.jts.geom.Geometry g1 = null;
            com.vividsolutions.jts.geom.Geometry g2 = null;

            /* Try to get hold of a database node. Otherwise, parse the geometry. */
            if (geom1x instanceof DBNode) {
                geom1db = (DBNode) geom1x;
            } else if (geom1x instanceof com.vividsolutions.jts.geom.Geometry) {
                g1 = (com.vividsolutions.jts.geom.Geometry) geom1x;
            } else {
                g1 = geoutils.singleObjectToJTSGeometry(geom1x);
            }

            if (geom2x instanceof DBNode) {
                geom2db = (DBNode) geom2x;
            } else if (geom2x instanceof com.vividsolutions.jts.geom.Geometry) {
                g2 = (com.vividsolutions.jts.geom.Geometry) geom2x;
            } else {
                g2 = geoutils.singleObjectToJTSGeometry(geom2x);
            }

            if (g1 == null) {
                if (geom1db != null) {
                    g1 = getOrCacheGeometry(geom1db);
                } else {
                    g1 = geoutils.singleObjectToJTSGeometry(geom1x);
                }
            }

            if (g2 == null) {
                if (geom2db != null) {
                    g2 = getOrCacheGeometry(geom2db);
                } else {
                    g2 = geoutils.singleObjectToJTSGeometry(geom2x);
                }
            }

            switch (op) {
            case CONTAINS:
                return g1.contains(g2);
            case CROSSES:
                return g1.crosses(g2);
            case EQUALS:
                return g1.equals(g2);
            case INTERSECTS:
                return g1.intersects(g2);
            case ISDISJOINT:
                return g1.disjoint(g2);
            case ISWITHIN:
                return g1.within(g2);
            case OVERLAPS:
                return g1.overlaps(g2);
            case TOUCHES:
                return g1.touches(g2);
            default:
                throw new IllegalArgumentException("Unknown spatial relationship operator: " + op.toString());
            }

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception occurred while applying spatial relationship operation (with single geometry to compare). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    // private boolean
    // applySpatialRelationshipOperator(com.vividsolutions.jts.geom.Geometry
    // geom1,
    // com.vividsolutions.jts.geom.Geometry geom2, SpatialRelOp op) {
    //
    // switch (op) {
    // case CONTAINS:
    // return geom1.contains(geom2);
    // case CROSSES:
    // return geom1.crosses(geom2);
    // case EQUALS:
    // return geom1.equals(geom2);
    // case INTERSECTS:
    // return geom1.intersects(geom2);
    // case ISDISJOINT:
    // return geom1.disjoint(geom2);
    // case ISWITHIN:
    // return geom1.within(geom2);
    // case OVERLAPS:
    // return geom1.overlaps(geom2);
    // case TOUCHES:
    // return geom1.touches(geom2);
    // default:
    // throw new IllegalArgumentException("Unknown spatial relationship
    // operator: " + op.toString());
    // }
    // }

    private boolean applySpatialRelationshipOperation(Object arg1, Object arg2, SpatialRelOp op, boolean matchAll)
            throws QueryException {

        try {

            if (arg1 == null || arg2 == null || arg1 instanceof Empty || arg2 instanceof Empty
                    || (arg1 instanceof GeometryCollection && ((GeometryCollection) arg1).isEmpty())
                    || (arg2 instanceof GeometryCollection && ((GeometryCollection) arg2).isEmpty())) {

                return false;

            } else {

                List<Object> arg1_list = toObjectList(arg1);
                List<Object> arg2_list = toObjectList(arg2);

                boolean allMatch = true;

                outer: for (Object o1 : arg1_list) {
                    for (Object o2 : arg2_list) {

                        if (matchAll) {

                            if (applySpatialRelationshipOperation(o1, o2, op)) {
                                /* check the next geometry pair to see if it also satisfies the spatial relationship */
                            } else {
                                allMatch = false;
                                break outer;
                            }

                        } else {

                            if (applySpatialRelationshipOperation(o1, o2, op)) {
                                return true;
                            } else {
                                /* check the next geometry pair to see if it satisfies the spatial relationship */
                            }
                        }
                    }
                }

                if (matchAll) {
                    return allMatch;
                } else {
                    return false;
                }
            }

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception occurred while applying spatial relationship operation (with multiple geometries to compare). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * @param o
     *            A Value, an Object[], a JTS geometry (also a geometry collection) or anything else
     * @return a list of single objects (Value of size > 1 is flattened to a list of Items, a JTS geometry collection is flattened as well)
     */
    private List<Object> toObjectList(Object o) {

        List<Object> result = new ArrayList<>();

        if (o instanceof Value) {

            Value v = (Value) o;

            if (v.size() > 1) {

                for (Item i : v) {
                    result.addAll(toObjectList(i));
                }

            } else {

                result.add(o);
            }

        } else if (o instanceof Object[]) {

            for (Object os : (Object[]) o) {

                result.addAll(toObjectList(os));
            }

        } else {

            if (o instanceof com.vividsolutions.jts.geom.Geometry) {
                com.vividsolutions.jts.geom.Geometry geom = (com.vividsolutions.jts.geom.Geometry) o;
                List<com.vividsolutions.jts.geom.Geometry> gc = geoutils.toFlattenedJTSGeometryCollection(geom);
                result.addAll(gc);
            } else {
                result.add(o);
            }
        }

        return result;
    }

    // private boolean performSpatialRelationshipOperation(Object arg1, Object
    // arg2, SpatialRelOp op, boolean matchAll)
    // throws QueryException {
    //
    // try {
    //
    // if (arg1 instanceof Empty || arg2 instanceof Empty) {
    //
    // return false;
    //
    // } else {
    //
    // com.vividsolutions.jts.geom.Geometry geom1, geom2;
    //
    // geom1 = geoutils.toJTSGeometry(arg1);
    // geom2 = geoutils.toJTSGeometry(arg2);
    //
    // List<com.vividsolutions.jts.geom.Geometry> gc1, gc2;
    //
    // gc1 = geoutils.toFlattenedJTSGeometryCollection(geom1);
    // gc2 = geoutils.toFlattenedJTSGeometryCollection(geom2);
    //
    // boolean allMatch = true;
    //
    // outer: for (com.vividsolutions.jts.geom.Geometry g1 : gc1) {
    // for (com.vividsolutions.jts.geom.Geometry g2 : gc2) {
    //
    // if (matchAll) {
    //
    // if (applySpatialRelationshipOperator(g1, g2, op)) {
    // /*
    // * check the next geometry pair to see if it
    // * also satisfies the spatial relationship
    // */
    // } else {
    // allMatch = false;
    // break outer;
    // }
    //
    // } else {
    //
    // if (applySpatialRelationshipOperator(g1, g2, op)) {
    // return true;
    // } else {
    // /*
    // * check the next geometry pair to see if it
    // * satisfies the spatial relationship
    // */
    // }
    // }
    // }
    // }
    //
    // if (matchAll) {
    // return allMatch;
    // } else {
    // return false;
    // }
    // }
    //
    // } catch (Exception e) {
    // throw new QueryException(e);
    // }
    // }

    // /**
    // * Tests if the first geometry is disjoint from the second geometry.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents the second geometry, encoded as a GML geometry
    // * element
    // * @return <code>true</code> if the first geometry is disjoint from the
    // * second one, else <code>false</code>.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean isDisjoint(Value arg1, Value arg2) throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.ISDISJOINT);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing isDisjoint(Value,Value). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }
    //
    // /**
    // * Tests if one geometry is disjoint to a list of geometries. Whether a
    // * match is required for all or just one of these is controlled via
    // * parameter.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents a list of geometries, encoded as a GML geometry
    // * element
    // * @param matchAll
    // * <code>true</code> if arg1 must fulfill the spatial
    // * relationship for all geometries in arg2, else
    // * <code>false</code>
    // * @return <code>true</code> if the conditions are met, else
    // * <code>false</code>. <code>false</code> will also be returned if
    // * arg2 is empty.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean isDisjoint(Object arg1, Object arg2, boolean matchAll)
    // throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.ISDISJOINT, matchAll);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing isDisjoint(Object,Object,boolean). Message is:
    // " + e.getMessage());
    //
    // throw qe;
    // }
    // }

    /**
     * Tests if the first and the second geometry are disjoint.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first and the second geometry are disjoint, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjoint(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISDISJOINT);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isDisjoint(Value,Value). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry is disjoint with a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjoint(Value arg1, Value arg2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.ISDISJOINT, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isDisjoint(Value,Value,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first and the second geometry are disjoint.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @return <code>true</code> if the first and the second geometry are disjoint, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjointGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISDISJOINT);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isDisjointGeomGeom(Geometry,Geometry). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry is disjoint with a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjointGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISDISJOINT, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isDisjointGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    // /**
    // * Tests if the first geometry is within the second geometry.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents the second geometry, encoded as a GML geometry
    // * element
    // * @return <code>true</code> if the first geometry is within the second
    // one,
    // * else <code>false</code>.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean isWithin(Value arg1, Value arg2) throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.ISWITHIN);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing isWithin(Value,Value). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }
    //
    // /**
    // * Tests if one geometry is within a list of geometries. Whether a match
    // is
    // * required for all or just one of these is controlled via parameter.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents a list of geometries, encoded as a GML geometry
    // * element
    // * @param matchAll
    // * <code>true</code> if arg1 must fulfill the spatial
    // * relationship for all geometries in arg2, else
    // * <code>false</code>
    // * @return <code>true</code> if the conditions are met, else
    // * <code>false</code>. <code>false</code> will also be returned if
    // * arg2 is empty.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean isWithin(Object arg1, Object arg2, boolean matchAll)
    // throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.ISWITHIN, matchAll);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing isWithin(Object,Object,boolean). Message is: "
    // + e.getMessage());
    //
    // throw qe;
    // }
    // }

    /**
     * Tests if the first geometry is within the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry is within the second geometry, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithin(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISWITHIN);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isWithin(Value,Value). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry is within a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithin(Value arg1, Value arg2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.ISWITHIN, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isWithin(Value,Value,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry is within the second geometry.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @return <code>true</code> if the first geometry is within the second geometry, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithinGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISWITHIN);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isWithinGeomGeom(Geometry,Geometry). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry is within a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithinGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISWITHIN, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isWithinGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    // /**
    // * Tests if the first geometry overlaps the second geometry.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents the second geometry, encoded as a GML geometry
    // * element
    // * @return <code>true</code> if the first geometry overlaps the second
    // one,
    // * else <code>false</code>.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean overlaps(Value arg1, Value arg2) throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.OVERLAPS);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing overlaps(Value,Value). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }
    //
    // /**
    // * Tests if one geometry overlaps a list of geometries. Whether a match is
    // * required for all or just one of these is controlled via parameter.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents a list of geometries, encoded as a GML geometry
    // * element
    // * @param matchAll
    // * <code>true</code> if arg1 must fulfill the spatial
    // * relationship for all geometries in arg2, else
    // * <code>false</code>
    // * @return <code>true</code> if the conditions are met, else
    // * <code>false</code>. <code>false</code> will also be returned if
    // * arg2 is empty.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean overlaps(Value arg1, Value arg2, boolean matchAll) throws
    // QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.OVERLAPS, matchAll);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing overlaps(Value,Value,boolean). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }

    /**
     * Tests if the first geometry overlaps the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry overlaps the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlaps(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.OVERLAPS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing overlaps(Value,Value). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry overlaps a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlaps(Value arg1, Value arg2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.OVERLAPS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing overlaps(Value,Value,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry overlaps the second geometry.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @return <code>true</code> if the first geometry intersects the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlapsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.OVERLAPS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing overlapsGeomGeom(Geometry,Geometry). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry overlaps a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlapsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.OVERLAPS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing overlapsGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    // /**
    // * Tests if the first geometry touches the second geometry.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents the second geometry, encoded as a GML geometry
    // * element
    // * @return <code>true</code> if the first geometry touches the second one,
    // * else <code>false</code>.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean touches(Value arg1, Value arg2) throws QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.TOUCHES);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing touches(Value,Value). Message is: " +
    // e.getMessage());
    //
    // throw qe;
    // }
    // }
    //
    // /**
    // * Tests if one geometry touches a list of geometries. Whether a match is
    // * required for all or just one of these is controlled via parameter.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of
    // supported
    // * and unsupported geometry types.
    // *
    // * @param arg1
    // * represents the first geometry, encoded as a GML geometry
    // * element
    // * @param arg2
    // * represents a list of geometries, encoded as a GML geometry
    // * element
    // * @param matchAll
    // * <code>true</code> if arg1 must fulfill the spatial
    // * relationship for all geometries in arg2, else
    // * <code>false</code>
    // * @return <code>true</code> if the conditions are met, else
    // * <code>false</code>. <code>false</code> will also be returned if
    // * arg2 is empty.
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public boolean touches(Object arg1, Object arg2, boolean matchAll) throws
    // QueryException {
    // try {
    // return applySpatialRelationshipOperation(arg1, arg2,
    // SpatialRelOp.TOUCHES, matchAll);
    // } catch (Exception e) {
    // QueryException qe = new QueryException(
    // "Exception while executing touches(Object,Object,boolean). Message is: "
    // + e.getMessage());
    //
    // throw qe;
    // }
    // }

    /**
     * Tests if the first geometry touches the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry touches the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean touches(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.TOUCHES);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing touches(Value,Value). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry touches a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean touches(Value arg1, Value arg2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.TOUCHES, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing touches(Value,Value,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry touches the second geometry.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @return <code>true</code> if the first geometry intersects the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean touchesGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.TOUCHES);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing touchesGeomGeom(Geometry,Geometry). Message is: " + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry touches a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean touchesGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.TOUCHES, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing touchesGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Adds the name of a GML geometry element to the set of elements for which validation is performed.
     *
     * @param nodeName
     *            name (simple, i.e. without namespace (prefix)) of a GML geometry element to validate.
     */
    @Requires(Permission.NONE)
    public void registerGmlGeometry(String nodeName) {
        gmlGeometries.add(nodeName);
    }

    /**
     * Set the standard SRS to use for a geometry if no srsName attribute is directly defined for it. Setting a standard SRS can improve performance, but should only be done if all geometry elements without srsName attribute have the same SRS.
     *
     * @param srsName
     *            name of the SRS to assign to a geometry if it does not have an srsName attribute itself.
     */
    @Requires(Permission.NONE)
    public void setStandardSRS(String srsName) throws QueryException {
        if (StringUtils.isBlank(srsName)) {
            throw new QueryException("Given parameter value is blank.");
        } else {
            this.standardSRS = srsName;
        }
    }

    /**
     * Removes the name of a GML geometry element from the set of elements for which validation is performed.
     *
     * @param nodeName
     *            name (simple, i.e. without namespace (prefix)) of a GML geometry element to remove from validation.
     */
    @Requires(Permission.NONE)
    public void unregisterGmlGeometry(String nodeName) {
        gmlGeometries.remove(nodeName);
    }

    /**
     * Removes all names of GML geometry elements that are currently registered for validation.
     */
    @Requires(Permission.NONE)
    public void unregisterAllGmlGeometries() {
        gmlGeometries.clear();
    }

    /**
     * @return the currently registered GML geometry element names (comma separated)
     */
    @Requires(Permission.NONE)
    public String registeredGmlGeometries() {

        if (gmlGeometries.isEmpty()) {
            return "";
        } else {
            Joiner joiner = Joiner.on(", ").skipNulls();
            return joiner.join(gmlGeometries);
        }
    }

    /**
     * Create the union of the given geometry objects.
     *
     * @param arg1
     *            - a single or collection of JTS geometries or geometry nodes.
     * @param arg2
     *            - a single or collection of JTS geometries or geometry nodes.
     * @return the union of the geometries - can be a JTS geometry collection
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry union(Object arg1, Object arg2) throws QueryException {

        try {

            List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

            com.vividsolutions.jts.geom.Geometry geom1 = geoutils.toJTSGeometry(arg1);
            geoms.add(geom1);

            com.vividsolutions.jts.geom.Geometry geom2 = geoutils.toJTSGeometry(arg2);
            geoms.add(geom2);

            com.vividsolutions.jts.geom.GeometryCollection gc = geoutils.toJTSGeometryCollection(geoms, true);

            return gc.union();

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Create the union of the given geometry objects.
     *
     * @param arg
     *            - a single or collection of JTS geometries or geometry nodes.
     * @return the union of the geometries - can be a JTS geometry collection
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry union(Object arg) throws QueryException {

        try {

            List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

            com.vividsolutions.jts.geom.Geometry geom = geoutils.toJTSGeometry(arg);
            geoms.add(geom);

            com.vividsolutions.jts.geom.GeometryCollection gc = geoutils.toJTSGeometryCollection(geoms, true);

            return gc.union();

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Create the union of the given geometry nodes.
     *
     * @param arg
     *            - a single or collection of geometry nodes.
     * @return the union of the geometries - can be a JTS geometry collection
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry unionNodes(Value val) throws QueryException {

        try {

            // first get all values
            List<Item> items = new ArrayList<>();

            for (Item i : val) {
                items.add(i);
            }

            /* Now, create unions from partitions of the list of items. */
            List<com.vividsolutions.jts.geom.Geometry> unions = new ArrayList<>();

            int x = (int) (Math.ceil((double) items.size() / 1000) - 1);

            for (int groupIndex = 0; groupIndex <= x; groupIndex++) {

                int groupStart = (int) ((x - groupIndex) * 1000);
                int groupEnd = (int) (((x - groupIndex) + 1) * 1000);

                if (groupEnd > items.size()) {
                    groupEnd = items.size();
                }

                List<Item> itemsSublist = items.subList(groupStart, groupEnd);

                List<com.vividsolutions.jts.geom.Geometry> geomsInSublist = new ArrayList<>();

                for (Item i : itemsSublist) {

                    com.vividsolutions.jts.geom.Geometry geom;
                    if (i instanceof DBNode) {
                        geom = getOrCacheGeometry((DBNode) i);
                    } else {
                        geom = geoutils.toJTSGeometry(i);
                    }
                    geomsInSublist.add(geom);
                }

                com.vividsolutions.jts.geom.GeometryCollection sublistGc = geoutils
                        .toJTSGeometryCollection(geomsInSublist, true);

                unions.add(sublistGc.union());
            }

            /* Finally, create a union from the list of unions. */
            com.vividsolutions.jts.geom.GeometryCollection unionsGc = geoutils.toJTSGeometryCollection(unions, true);

            return unionsGc.union();

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception occurred while applying unionNodes(..)). Message is: " + e.getMessage());
            throw qe;
        }
    }

    /**
     * Check if a JTS geometry is empty.
     *
     * @param geom
     * @return <code>true</code> if the geometry is <code>null</code> or empty, else <code>false</code>.
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isEmpty(com.vividsolutions.jts.geom.Geometry geom) {

        if (geom == null || geom.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if a given object is closed. The object can be a single geometry or a collection of geometries. Only LineStrings and MultiLineStrings are checked.
     *
     * NOTE: Invokes the {@link #isClosed(Object, boolean)} method, with <code>true</code> for the second parameter.
     *
     * @see #isClosed(Object, boolean)
     * @param o
     * @return
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosed(Object o) throws QueryException {
        return isClosed(o, true);
    }

    /**
     * Checks if a given object is closed. The object can be a single geometry or a collection of geometries. Points and MultiPoints are closed by definition (they do not have a boundary). Polygons and MultiPolygons are never closed in 2D, and since operations in 3D are not supported, this method will always return <code>false</code> if a polygon is encountered - unless the parameter onlyCheckCurveGeometries is set to <code>true</code>. LinearRings are closed by definition. The remaining geometry types that will be checked are LineString and MultiLineString. If a (Multi)LineString is not closed, this method will return <code>false</code>.
     *
     * @param o
     *            the geometry object(s) to test, can be a JTS geometry object, collection, and BaseX nodes (that will be converted to JTS geometries)
     * @param onlyCheckCurveGeometries
     *            <code>true</code> if only curve geometries (i.e., for JTS: LineString, LinearRing, and MultiLineString) shall be tested, else <code>false</code> (in this case, the occurrence of polygons will result in the return value <code>false</code>).
     * @return <code>true</code> if the given object - a geometry or collection of geometries - is closed, else <code>false</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosed(Object o, boolean onlyCheckCurveGeometries) throws QueryException {

        try {

            if (o instanceof Empty) {

                return true;

            } else {

                com.vividsolutions.jts.geom.Geometry geom = geoutils.toJTSGeometry(o);

                List<com.vividsolutions.jts.geom.Geometry> gc = geoutils.toFlattenedJTSGeometryCollection(geom);

                for (com.vividsolutions.jts.geom.Geometry g : gc) {

                    if (g instanceof com.vividsolutions.jts.geom.Point
                            || g instanceof com.vividsolutions.jts.geom.MultiPoint) {

                        /* points are closed by definition (they do not have a boundary) */

                    } else if (g instanceof com.vividsolutions.jts.geom.Polygon
                            || g instanceof com.vividsolutions.jts.geom.MultiPolygon) {

                        /* The JTS FAQ contains the following question and answer:
                         *
                         * Question: Does JTS support 3D operations?
                         *
                         * Answer: JTS does not provide support for true 3D geometry and operations. However, JTS does allow Coordinates to carry an elevation or Z value. This does not provide true 3D support, but does allow "2.5D" uses which are required in some geospatial applications.
                         *
                         * -------
                         *
                         * So, JTS does not support true 3D geometry and operations. Therefore, JTS cannot determine if a surface is closed. deegree does not seem to support this, either. In order for a surface to be closed, it must be a sphere or torus, possibly with holes. A surface in 2D can never be closed. Since we lack the ability to compute in 3D we assume that a (Multi)Polygon is not closed. If we do check geometries other than curves, then we return false. */
                        if (!onlyCheckCurveGeometries) {
                            return false;
                        }

                    } else if (g instanceof com.vividsolutions.jts.geom.MultiLineString) {

                        com.vividsolutions.jts.geom.MultiLineString mls = (com.vividsolutions.jts.geom.MultiLineString) g;
                        if (!mls.isClosed()) {
                            return false;
                        }

                    } else if (g instanceof com.vividsolutions.jts.geom.LineString) {

                        /* NOTE: LinearRing is a subclass of LineString, and closed by definition */

                        com.vividsolutions.jts.geom.LineString ls = (com.vividsolutions.jts.geom.LineString) g;
                        if (!ls.isClosed()) {
                            return false;
                        }

                    } else {
                        // should not happen
                        throw new Exception("Unexpected geometry type encountered: " + g.getClass().getName());
                    }
                }

                // all relevant geometries are closed
                return true;
            }

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Identifies the holes contained in the given geometry and returns them as a JTS geometry. If holes were found a union is built, to ensure that the result is a valid JTS Polygon or JTS MultiPolygon. If no holes were found an empty JTS GeometryCollection is returned.
     *
     * @param geom
     *            potentially existing holes will be extracted from this geometry (can be a Polygon, MultiPolygon, or any other JTS geometry)
     * @return A geometry (JTS Polygon or MultiPolygon) with the holes contained in the given geometry. Can also be an empty JTS GeometryCollection but not <code>null</code>;
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry holes(com.vividsolutions.jts.geom.Geometry geom) {

        if (isEmpty(geom)) {

            return geoutils.emptyJTSGeometry();

        } else {

            List<com.vividsolutions.jts.geom.Geometry> holes = computeHoles(geom);

            if (holes.isEmpty()) {
                return geoutils.emptyJTSGeometry();
            } else {
                // create union of holes and return it
                return CascadedPolygonUnion.union(holes);
            }
        }
    }

    /**
     * Identifies the holes contained in the given geometry and returns them as polygons within a JTS geometry collection.
     *
     * @param geom
     *            potentially existing holes will be extracted from this geometry (can be a Polygon, MultiPolygon, or any other JTS geometry)
     * @return A JTS geometry collection with the holes (as polygons) contained in the given geometry. Can be empty but not <code>null</code>;
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry holesAsGeometryCollection(com.vividsolutions.jts.geom.Geometry geom) {

        if (isEmpty(geom)) {

            return geoutils.emptyJTSGeometry();

        } else {

            List<com.vividsolutions.jts.geom.Geometry> holes = computeHoles(geom);

            if (holes.isEmpty()) {
                return geoutils.emptyJTSGeometry();
            } else {
                return geoutils.toJTSGeometryCollection(holes, true);
            }
        }
    }

    protected List<com.vividsolutions.jts.geom.Geometry> computeHoles(com.vividsolutions.jts.geom.Geometry geom) {

        List<com.vividsolutions.jts.geom.Geometry> holes = new ArrayList<>();

        List<com.vividsolutions.jts.geom.Polygon> extractedPolygons = new ArrayList<>();

        GeometryExtracter.extract(geom, com.vividsolutions.jts.geom.Polygon.class, extractedPolygons);

        if (!extractedPolygons.isEmpty()) {

            // get holes as polygons

            for (com.vividsolutions.jts.geom.Polygon polygon : extractedPolygons) {

                // check that polygon has holes
                if (polygon.getNumInteriorRing() > 0) {

                    // for each hole, convert it to a polygon
                    for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                        com.vividsolutions.jts.geom.LineString ls = polygon.getInteriorRingN(i);
                        com.vividsolutions.jts.geom.Polygon holeAsPolygon = geoutils.toJTSPolygon(ls);
                        holes.add(holeAsPolygon);
                    }
                }
            }
        }

        return holes;
    }

    /**
     * Check if a given geometry node is valid.
     *
     * @param node
     * @return <code>true</code> if the given node represents a valid geometry, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public boolean isValid(ANode geometryNode) throws QueryException {

        String validationResult = validate(geometryNode);

        if (validationResult.toLowerCase().indexOf('f') > -1) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Tests if the first geometry relates to the second geometry as defined by the given intersection pattern.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @param intersectionPattern
     *            the pattern against which to check the intersection matrix for the two geometries (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
     * @return <code>true</code> if the DE-9IM intersection matrix for the two geometries matches the <code>intersectionPattern</code>, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean relate(ANode arg1, ANode arg2, String intersectionPattern) throws QueryException {

        try {

            checkIntersectionPattern(intersectionPattern);
            return applyRelate(arg1, arg2, intersectionPattern);

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing relate(Value,Value,String). Message is: " + e.getMessage());

            throw qe;
        }
    }

    private boolean applyRelate(Object geom1x, Object geom2x, String intersectionPattern) throws QueryException {

        if (geom1x instanceof Empty || geom2x instanceof Empty) {
            return false;
        }

        try {
            DBNode geom1db = null;
            DBNode geom2db = null;

            com.vividsolutions.jts.geom.Geometry g1 = null;
            com.vividsolutions.jts.geom.Geometry g2 = null;

            /* Try to get hold of a database node. Otherwise, parse the geometry. */
            if (geom1x instanceof DBNode) {
                geom1db = (DBNode) geom1x;
            } else {
                g1 = geoutils.singleObjectToJTSGeometry(geom1x);
            }

            if (geom2x instanceof DBNode) {
                geom2db = (DBNode) geom2x;
            } else {
                g2 = geoutils.singleObjectToJTSGeometry(geom2x);
            }

            if (g1 == null) {
                if (geom1db != null) {
                    g1 = getOrCacheGeometry(geom1db);
                } else {
                    g1 = geoutils.singleObjectToJTSGeometry(geom1x);
                }
            }

            if (g2 == null) {
                if (geom2db != null) {
                    g2 = getOrCacheGeometry(geom2db);
                } else {
                    g2 = geoutils.singleObjectToJTSGeometry(geom2x);
                }
            }

            return g1.relate(g2, intersectionPattern);

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Tests if one geometry relates to a list of geometries as defined by the given intersection pattern. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements
     * @param intersectionPattern
     *            the pattern against which to check the intersection matrix for the geometries (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship defined by the <code>intersectionPattern</code> for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean relate(Value arg1, Value arg2, String intersectionPattern, boolean matchAll) throws QueryException {

        try {

            checkIntersectionPattern(intersectionPattern);
            return applyRelate(arg1, arg2, intersectionPattern, matchAll);

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing relate(Value,Value,String,boolean). Message is: " + e.getMessage());

            throw qe;
        }
    }

    private boolean applyRelate(Object arg1, Object arg2, String intersectionPattern, boolean matchAll)
            throws QueryException {

        try {

            if (arg1 instanceof Empty || arg2 instanceof Empty) {

                return false;

            } else {

                List<Object> arg1_list = toObjectList(arg1);
                List<Object> arg2_list = toObjectList(arg2);

                boolean allMatch = true;

                outer: for (Object o1 : arg1_list) {
                    for (Object o2 : arg2_list) {

                        if (matchAll) {

                            if (applyRelate(o1, o2, intersectionPattern)) {
                                /* check the next geometry pair to see if it also satisfies the spatial relationship */
                            } else {
                                allMatch = false;
                                break outer;
                            }

                        } else {

                            if (applyRelate(o1, o2, intersectionPattern)) {
                                return true;
                            } else {
                                /* check the next geometry pair to see if it satisfies the spatial relationship */
                            }
                        }
                    }
                }

                if (matchAll) {
                    return allMatch;
                } else {
                    return false;
                }
            }

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Tests if the first geometry relates to the second geometry as defined by the given intersection pattern.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents the second geometry, encoded as a JTS geometry object
     * @param intersectionPattern
     *            the pattern against which to check the intersection matrix for the two geometries (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
     * @return <code>true</code> if the DE-9IM intersection matrix for the two geometries matches the <code>intersectionPattern</code>, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean relateGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, String intersectionPattern) throws QueryException {

        try {

            checkIntersectionPattern(intersectionPattern);
            return applyRelate(geom1, geom2, intersectionPattern);

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing relateGeomGeom(Geometry,Geometry,String). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry relates to a list of geometries as defined by the given intersection pattern. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param intersectionPattern
     *            the pattern against which to check the intersection matrix for the geometries (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean relateGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, String intersectionPattern, boolean matchAll)
            throws QueryException {

        try {

            checkIntersectionPattern(intersectionPattern);
            return applyRelate(geom1, geom2, intersectionPattern, matchAll);

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing relateGeomGeom(Geometry,Geometry,String,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    private void checkIntersectionPattern(String intersectionPattern) throws QueryException {
        if (intersectionPattern == null) {
            throw new QueryException("intersectionPattern is null.");
        } else {
            final Matcher m = INTERSECTIONPATTERN.matcher(intersectionPattern.trim());
            if (!m.matches()) {
                throw new QueryException(
                        "intersectionPattern does not match the regular expression, which is: [0-2\\\\*TF]{9}");
            }
        }

    }

    /**
     * Computes the intersection between the first and the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry1
     *            represents the first geometry
     * @param geometry2
     *            represents the second geometry
     * @return the point-set common to the two geometries
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry intersection(final Object geometry1, final Object geometry2)
            throws QueryException {
        try {
            if (geometry1 instanceof Empty || geometry2 instanceof Empty) {
                return geoutils.emptyJTSGeometry();
            } else {

                final com.vividsolutions.jts.geom.Geometry geom1, geom2;
                geom1 = geoutils.toJTSGeometry(geometry1);
                geom2 = geoutils.toJTSGeometry(geometry2);
                return geom1.intersection(geom2);
            }
        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * Computes the difference between the first and the second geometry node.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry1
     *            represents the first geometry
     * @param geometry2
     *            represents the second geometry
     * @return the closure of the point-set of the points contained in geometry1 that are not contained in geometry2.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry difference(final ANode geometry1, final ANode geometry2)
            throws QueryException {
        try {

            final com.vividsolutions.jts.geom.Geometry geom1 = getOrCacheGeometry(geometry1);
            final com.vividsolutions.jts.geom.Geometry geom2 = getOrCacheGeometry(geometry2);
            return geom1.difference(geom2);

        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * @see {{@link #boundaryGeom(com.vividsolutions.jts.geom.Geometry)}
     * @param geometryNode
     * @return
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry boundary(final ANode geometryNode) throws QueryException {

        return boundaryGeom(getOrCacheGeometry(geometryNode));
    }

    /**
     * Returns the boundary, or an empty geometry of appropriate dimension if the given geometry is empty.(In the case of zero-dimensional geometries, 'an empty GeometryCollection is returned.) For a discussion of this function, see the OpenGIS SimpleFeatures Specification. As stated in SFS Section 2.1.13.1, "the boundary of a Geometry is a set of Geometries of the next lower dimension."
     *
     * @param geometry
     * @returns the closure of the combinatorial boundary of this Geometry
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry boundaryGeom(final com.vividsolutions.jts.geom.Geometry geometry)
            throws QueryException {

        try {
            if (geometry == null) {
                return geoutils.emptyJTSGeometry();
            } else {
                return geometry.getBoundary();
            }

        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * Computes the difference between the first and the second geometry.
     *
     * @param geometry1
     *            the first geometry
     * @param geometry2
     *            the second geometry
     * @return the closure of the point-set of the points contained in geometry1 that are not contained in geometry2.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry differenceGeomGeom(final com.vividsolutions.jts.geom.Geometry geometry1,
            final com.vividsolutions.jts.geom.Geometry geometry2) throws QueryException {
        try {

            return geometry1.difference(geometry2);

        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    // /**
    // * Computes the intersection between the first and the second geometry.
    // * <p>
    // * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
    // and
    // * unsupported geometry types.
    // *
    // * @param geometry1 represents the first geometry
    // * @param geometry2 represents the second geometry
    // * @return the point-set common to the two geometries
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public com.vividsolutions.jts.geom.Geometry intersection(final ANode
    // geometry1, final ANode geometry2)
    // throws QueryException {
    //
    // try {
    //
    // final com.vividsolutions.jts.geom.Geometry geom1, geom2;
    // geom1 = getOrCacheGeometry(geometry1);
    // geom2 = getOrCacheGeometry(geometry2);
    // return geom1.intersection(geom2);
    //
    // } catch (Exception e) {
    // throw new QueryException(e);
    // }
    //
    // }
    //
    // /**
    // * Computes the intersection between the first and the second geometry.
    // *
    // * @param geometry1 the first geometry
    // * @param geometry2 the second geometry
    // * @return the point-set common to the two geometries
    // * @throws QueryException
    // */
    // @Requires(Permission.NONE)
    // @Deterministic
    // public com.vividsolutions.jts.geom.Geometry intersectionGeomGeom(
    // final com.vividsolutions.jts.geom.Geometry geometry1, final
    // com.vividsolutions.jts.geom.Geometry geometry2)
    // throws QueryException {
    //
    // try {
    // return geometry1.intersection(geometry2);
    // } catch (Exception e) {
    // throw new QueryException(e);
    // }
    // }

    /**
     * Computes the envelope of a geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry
     *            represents the geometry
     * @return The bounding box, an array { x1, y1, x2, y2 }
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public Object[] envelope(Object geometry) throws QueryException {
        try {
            final Envelope env;
            if (geometry instanceof Empty) {
                env = geoutils.emptyJTSGeometry().getEnvelopeInternal();
            } else if (geometry instanceof com.vividsolutions.jts.geom.Geometry) {
                env = ((com.vividsolutions.jts.geom.Geometry) geometry).getEnvelopeInternal();
            } else {
                env = geoutils.toJTSGeometry(geometry).getEnvelopeInternal();
            }
            final Object[] res = {env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()};
            return res;
        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * Retrieve the pre value of the BaseX DBNode that is represented by a given DBNodeEntry.
     *
     * @param entry
     *            - a DBNodeEntry
     * @return a pre value
     */
    @Requires(Permission.NONE)
    @Deterministic
    public int pre(Object entry) {
        if (entry instanceof DBNodeEntry)
            return ((DBNodeEntry) entry).pre;

        return -1;
    }

    /**
     * Retrieve the database name of the BaseX DBNode that is represented by a given DBNodeEntry.
     *
     * @param entry
     *            - a DBNodeEntry
     * @return a database name
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String dbname(Object entry) {
        if (entry instanceof DBNodeEntry)
            return ((DBNodeEntry) entry).dbname;

        return null;
    }

    /**
     * Searches the default spatial r-tree index for items whose minimum bounding box intersects with the rectangle defined by the given coordinates.
     *
     * @param minx
     *            represents the minimum value on the first coordinate axis; a number
     * @param miny
     *            represents the minimum value on the second coordinate axis; a number
     * @param maxx
     *            represents the maximum value on the first coordinate axis; a number
     * @param maxy
     *            represents the maximum value on the second coordinate axis; a number
     * @return the node set of all items in the envelope
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(Object minx, Object miny, Object maxx, Object maxy) throws QueryException {
        return search(null, minx, miny, maxx, maxy);
    }

    /**
     * Searches the named spatial r-tree index for items whose minimum bounding box intersects with the rectangle defined by the given coordinates.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param minx
     *            represents the minimum value on the first coordinate axis; a number
     * @param miny
     *            represents the minimum value on the second coordinate axis; a number
     * @param maxx
     *            represents the maximum value on the first coordinate axis; a number
     * @param maxy
     *            represents the maximum value on the second coordinate axis; a number
     * @return the node set of all items in the envelope
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(final String indexName, Object minx, Object miny, Object maxx, Object maxy)
            throws QueryException {

        try {
            double x1;
            double x2;
            double y1;
            double y2;
            if (minx instanceof Number)
                x1 = ((Number) minx).doubleValue();
            else
                x1 = 0.0;
            if (miny instanceof Number)
                y1 = ((Number) miny).doubleValue();
            else
                y1 = 0.0;
            if (maxx instanceof Number)
                x2 = ((Number) maxx).doubleValue();
            else
                x2 = 0.0;
            if (maxy instanceof Number)
                y2 = ((Number) maxy).doubleValue();
            else
                y2 = 0.0;

            return performSearch(indexName, x1, y1, x2, y2);

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    private DBNode[] performSearch(String indexName, double x1, double y1, double x2, double y2) throws QueryException {

        Iterable<DBNodeEntry> iter = mgr.search(indexName, Geometries.rectangle(x1, y1, x2, y2));
        List<DBNode> nodelist = new ArrayList<DBNode>();
        if (iter != null) {
            for (DBNodeEntry entry : iter) {
                Data d = queryContext.resources.database(entry.dbname, new InputInfo("xpath", 0, 0));
                DBNode n = new DBNode(d, entry.pre, entry.nodeKind);
                if (n != null)
                    nodelist.add(n);
            }
        }

        if (debug && ++count % 5000 == 0) {
            String debugIndexName = indexName != null ? indexName : "default";
            logMemUsage("GmlGeoX#search " + count + ". Box: (" + x1 + ", " + y1 + ") (" + x2 + ", " + y2 + ")"
                    + ". Hits: " + nodelist.size() + " (in index '" + debugIndexName + "')");
        }

        return nodelist.toArray(new DBNode[nodelist.size()]);
    }

    /**
     * Searches the default spatial r-tree index for items whose minimum bounding box intersects with the the minimum bounding box of the given geometry node.
     *
     * @param geometryNode
     *
     * @return the node set of all items in the envelope
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(ANode geometryNode) throws QueryException {
        return search(null, geometryNode);
    }

    /**
     * Searches the named spatial r-tree index for items whose minimum bounding box intersects with the the minimum bounding box of the given geometry node.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param geometryNode
     *
     * @return the node set of all items in the envelope
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(final String indexName, ANode geometryNode) throws QueryException {

        com.vividsolutions.jts.geom.Geometry geom = getOrCacheGeometry(geometryNode);

        if (geom.isEmpty()) {
            throw new QueryException("Geometry determined for the given node is empty "
                    + "(ensure that the given node is a geometry node that represents a non-empty geometry). "
                    + "Cannot perform a search based upon an empty geometry.");
        }

        return searchGeom(indexName, geom);
    }

    /**
     * Searches the default spatial r-tree index for items whose minimum bounding box intersects with the the minimum bounding box of the given geometry.
     *
     * @param geom
     *
     * @return the node set of all items in the envelope
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] searchGeom(com.vividsolutions.jts.geom.Geometry geom) throws QueryException {
        return searchGeom(null, geom);
    }

    /**
     * Searches the named spatial r-tree index for items whose minimum bounding box intersects with the the minimum bounding box of the given geometry.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param geom
     *
     * @return the node set of all items in the envelope
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] searchGeom(final String indexName, com.vividsolutions.jts.geom.Geometry geom)
            throws QueryException {

        if (geom.isEmpty()) {
            throw new QueryException("Geometry is empty. Cannot perform a search based upon an empty geometry.");
        }

        try {

            Envelope env = geom.getEnvelopeInternal();
            double x1 = env.getMinX();
            double x2 = env.getMaxX();
            double y1 = env.getMinY();
            double y2 = env.getMaxY();

            return performSearch(indexName, x1, y1, x2, y2);

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Returns all items in the default spatial r-tree index.
     *
     * @return the node set of all items in the index
     * @throws QueryException
     */
    public DBNode[] search() throws QueryException {
        return searchInIndex(null);
    }

    /**
     * Returns all items in the named spatial r-tree index.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @return the node set of all items in the index
     * @throws QueryException
     */
    public DBNode[] searchInIndex(final String indexName) throws QueryException {

        try {
            logMemUsage("GmlGeoX#search.start " + count + ".");

            Iterable<DBNodeEntry> iter = mgr.search(indexName);
            List<DBNode> nodelist = new ArrayList<DBNode>();
            if (iter != null) {
                for (DBNodeEntry entry : iter) {
                    Data d = queryContext.resources.database(entry.dbname, new InputInfo("xpath", 0, 0));
                    DBNode n = new DBNode(d, entry.pre, entry.nodeKind);
                    if (n != null)
                        nodelist.add(n);
                }
            }

            String debugIndexName = indexName != null ? indexName : "default";
            logMemUsage(
                    "GmlGeoX#search " + count + ". Hits: " + nodelist.size() + " (in index '" + debugIndexName + "')");

            return nodelist.toArray(new DBNode[nodelist.size()]);

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Logs memory information if Logger is enabled for the DEBUG level
     *
     * @param progress
     *            status string
     */

    private void logMemUsage(final String progress) {
        if (debug) {
            final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            memory.gc();
            LOGGER.debug(progress + ". Memory: " + Math.round(memory.getHeapMemoryUsage().getUsed() / 1048576)
                    + " MB of " + Math.round(memory.getHeapMemoryUsage().getMax() / 1048576) + " MB.");
        }
    }

    /**
     * Set cache size for geometries
     *
     * @param size
     *            the size of the geometry cache; default is 100000
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void cacheSize(Object size) throws QueryException {
        if (size instanceof BigInteger) {
            mgr = new GeometryManager(((BigInteger) size).intValue());
        }
    }

    /**
     * Indexes a feature geometry, using the default index.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param node
     *            represents the indexed item node (can be the gml:id of a GML feature element, or the element itself)
     * @param geometry
     *            represents the GML geometry to index
     *
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void index(final ANode node, final ANode geometry) throws QueryException {
        index(null, node, geometry);
    }

    /**
     * Removes the named spatial index. WARNING: Be sure you know what you are doing.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void removeIndex(final String indexName) throws QueryException {
        mgr.removeIndex(indexName);
        /* NOTE: We do not remove a possibly existing entry in geometryIndexEntriesByIndexName, for a case in which the spatial index exists (e.g. from previous tests), but the query developer prepares the spatial index with new entries before removing the 'old' index. The typical sequence, however, should be to remove the old index first, then prepare and build the index with the new entries. */
    }

    /**
     * Indexes a feature geometry, using the named spatial index.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param node
     *            represents the indexed item node (can be the gml:id of a GML feature element, or the element itself)
     * @param geometry
     *            represents the GML geometry to index
     *
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void index(final String indexName, final ANode node, final ANode geometry) throws QueryException {

        if (node instanceof DBNode && geometry instanceof DBNode) {

            try {
                final com.vividsolutions.jts.geom.Geometry _geom = getOrCacheGeometry(geometry);
                final Envelope env = _geom.getEnvelopeInternal();
                if (!env.isNull()) {
                    final DBNodeEntry entry = new DBNodeEntry((DBNode) node);
                    if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {
                        mgr.index(indexName, entry, Geometries.point(env.getMinX(), env.getMinY()));
                    } else {
                        mgr.index(indexName, entry,
                                Geometries.rectangle(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()));
                    }
                }

                if (debug && mgr.indexSize(indexName) % 5000 == 0) {
                    String debugIndexName = indexName != null ? indexName : "default";
                    logMemUsage(
                            "GmlGeoX#index progress (for index '" + debugIndexName + "'): " + mgr.indexSize(indexName));
                }

            } catch (final Exception e) {
                if (e instanceof XMLParsingException) {
                    // otherwise the stacktrace "<< is empty >>" is included
                    throw new QueryException(prepareXMLParsingException((XMLParsingException) e));
                }
                throw new QueryException(e);
            }
        }
    }

    /**
     * Remove character offset information from the exception message, because in UnitTests on different machines different offsets were reported.
     *
     * @param e
     * @return
     */
    private String prepareXMLParsingException(XMLParsingException e) {

        String message = e.getMessage();
        message = message.replaceAll(", character offset: \\d+", "");

        return message;
    }

    /**
     * Checks if the coordinates of the given {@code point} are equal (comparing x, y, and z) to the coordinates of one of the points that define the given {@code geometry}.
     *
     * @param point
     *            The point whose coordinates are checked against the coordinates of the points of {@code geometry}
     * @param geom
     *            The geometry whose points are checked to see if one of them has coordinates equal to that of {@code point}
     * @return <code>true</code> if the coordinates of the given {@code point} are equal to the coordinates of one of the points that define {@code geometry}, else <code>false</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean pointCoordInGeometryCoords(com.vividsolutions.jts.geom.Point point,
            com.vividsolutions.jts.geom.Geometry geometry) throws QueryException {

        Coordinate pointCoord = point.getCoordinate();

        Coordinate[] geomCoords = geometry.getCoordinates();

        for (Coordinate geomCoord : geomCoords) {
            if (pointCoord.equals3D(geomCoord)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Retrieve the geometry represented by a given node as a JTS geometry. First try the cache and if it is not in the cache construct it from the XML.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geomNode
     *            the node that represents the geometry
     * @return the geometry of the node; can be an empty geometry if the node does not represent a geometry
     *
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry getOrCacheGeometry(ANode geomNode) throws QueryException {

        if (debug && ++count2 % 5000 == 0) {
            logMemUsage("GmlGeoX#getGeometry.start " + count2);
        }

        com.vividsolutions.jts.geom.Geometry geom;

        if (geomNode instanceof DBNode) {

            String geomId = getDBNodeID((DBNode) geomNode);

            try {
                geom = mgr.get(geomId);
            } catch (Exception e1) {
                throw new QueryException(
                        "Exception occurred while getting JTS geometry from GeometryManager. Exception message is: "
                                + e1.getMessage());
            }

            if (geom == null) {

                try {

                    geom = geoutils.singleObjectToJTSGeometry(geomNode);

                    if (geom != null) {
                        mgr.put(geomId, geom);
                    }

                } catch (Exception e) {
                    throw new QueryException(e);
                }

                if (debug && mgr.getMissCount() % 10000 == 0) {
                    LOGGER.debug("Cache misses: " + mgr.getMissCount() + " of " + mgr.getCount());
                }
            }

        } else {

            try {
                geom = geoutils.singleObjectToJTSGeometry(geomNode);

            } catch (Exception e) {
                throw new QueryException(e);
            }
        }

        if (geom == null) {
            geom = geoutils.emptyJTSGeometry();
        }

        if (debug && count2 % 5000 == 0) {
            logMemUsage("GmlGeoX#getGeometry.end " + count2);
        }

        return geom;
    }

    /**
     * @param dbn
     * @return pre + database name
     */
    private String getDBNodeID(DBNode dbn) {

        int pre = dbn.pre();
        String dbname = "";

        Data data = dbn.data();

        if (data != null && data.meta != null && data.meta.name != null) {
            dbname = data.meta.name;
        }
        return pre + dbname;
    }

    /**
     * Prepares spatial indexing of a feature geometry, for the default spatial index.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param node
     *            represents the node of the feature to be indexed
     * @param geometry
     *            represents the GML geometry to index
     *
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void prepareSpatialIndex(final ANode node, final ANode geometry) throws QueryException {

        prepareSpatialIndex(null, node, geometry);
    }

    /**
     * Prepares spatial indexing of a feature geometry, for the named spatial index.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param node
     *            represents the node of the feature to be indexed
     * @param geometry
     *            represents the GML geometry to index
     *
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void prepareSpatialIndex(final String indexName, final ANode node, final ANode geometry)
            throws QueryException {

        if (node instanceof DBNode && geometry instanceof DBNode) {

            try {

                final com.vividsolutions.jts.geom.Geometry _geom = getOrCacheGeometry(geometry);

                final Envelope env = _geom.getEnvelopeInternal();

                if (!env.isNull()) {

                    final DBNodeEntry entry = new DBNodeEntry((DBNode) node);

                    String key = indexName != null ? indexName : "";
                    List<Entry<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>> geomIndexEntries;
                    if (geomIndexEntriesByIndexName.containsKey(key)) {
                        geomIndexEntries = geomIndexEntriesByIndexName.get(key);
                    } else {
                        geomIndexEntries = new ArrayList<>();
                        geomIndexEntriesByIndexName.put(key, geomIndexEntries);
                    }

                    if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {

                        geomIndexEntries
                                .add(new EntryDefault<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>(entry,
                                        Geometries.point(env.getMinX(), env.getMinY())));

                    } else {

                        geomIndexEntries
                                .add(new EntryDefault<DBNodeEntry, com.github.davidmoten.rtree.geometry.Geometry>(entry,
                                        Geometries.rectangle(env.getMinX(), env.getMinY(), env.getMaxX(),
                                                env.getMaxY())));
                    }
                }

                if (debug && mgr.indexSize(indexName) % 5000 == 0) {
                    String debugIndexName = indexName != null ? indexName : "default";
                    logMemUsage(
                            "GmlGeoX#index progress (for index '" + debugIndexName + "'): " + mgr.indexSize(indexName));
                }
            } catch (final Exception e) {
                if (e instanceof XMLParsingException) {
                    // otherwise the stacktrace "<< is empty >>" is included
                    throw new QueryException(prepareXMLParsingException((XMLParsingException) e));
                }
                throw new QueryException(e);
            }
        }
    }

    /**
     * Create the default spatial index using bulk loading.
     * <p/>
     * Uses the index entries that have been prepared using method(s) prepareSpatialIndex(...).
     *
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void buildSpatialIndex() throws QueryException {

        buildSpatialIndex(null);
    }

    /**
     * Create the named spatial index using bulk loading.
     * <p/>
     * Uses the index entries that have been prepared using method(s) prepareSpatialIndex(...).
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void buildSpatialIndex(final String indexName) throws QueryException {
        String key = indexName != null ? indexName : "";
        if (geomIndexEntriesByIndexName.containsKey(key)) {
            mgr.index(indexName, geomIndexEntriesByIndexName.get(key));
            geomIndexEntriesByIndexName.remove(key);
        } else {
            throw new QueryException("Spatial index '" + key
                    + "' has already been built using bulk loading, or no entries for that index have been added using prepareSpatialIndex(...).");
        }
    }

    /**
     * Retrieve the first two coordinates of a given geometry.
     *
     * @param geom
     * @return an empty array if the geometry is null or empty, otherwise an array with the x and y from the first coordinate of the geometry
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String[] georefFromGeom(com.vividsolutions.jts.geom.Geometry geom) throws QueryException {

        if (geom == null || geom.isEmpty()) {
            return new String[]{};
        } else {
            Coordinate firstCoord = geom.getCoordinates()[0];
            return new String[]{"" + firstCoord.x, "" + firstCoord.y};
        }
    }

}
