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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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

import com.github.davidmoten.rtree.geometry.Geometries;
import com.google.common.base.Joiner;
import com.vividsolutions.jts.algorithm.Angle;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.IntersectionMatrix;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.util.GeometryExtracter;
import com.vividsolutions.jts.index.strtree.STRtree;
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
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.primitive.Curve;
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

    private static final Pattern INTERSECTIONPATTERN = Pattern
            .compile("[0-2\\*TF]{9}");

    private static final String SRS_SEARCH_QUERY = "declare namespace xlink='http://www.w3.org/1999/xlink';"
            + " declare variable $geom external; let $adv := root($geom)/*[local-name() = ('AX_Bestandsdatenauszug','AX_NutzerbezogeneBestandsdatenaktualisierung_NBA','AA_Fortfuehrungsauftrag','AX_Einrichtungsauftrag')]"
            + " return if($adv) then $adv/*:koordinatenangaben/*[xs:boolean(*:standard)]/*:crs/data(@xlink:href)"
            + " else let $ancestors := $geom/ancestor::* let $ancestorWithSrsName := $ancestors[@srsName][1]"
            + " return if($ancestorWithSrsName) then $ancestorWithSrsName/data(@srsName)"
            + " else $ancestors[*:boundedBy/*:Envelope/@srsName][1]/*:boundedBy/*:Envelope/data(@srsName)";

    private static final String SRS_SEARCH_QUERY_GEOMETRY_COMPONENT = "declare namespace xlink='http://www.w3.org/1999/xlink';"
            + " declare variable $geomComp external; let $ancestors := $geomComp/ancestor::* let $ancestorWithSrsName := $ancestors[@srsName][1]"
            + " return if($ancestorWithSrsName) then $ancestorWithSrsName/data(@srsName)"
            + " else let $adv := root($geomComp)/*[local-name() = ('AX_Bestandsdatenauszug','AX_NutzerbezogeneBestandsdatenaktualisierung_NBA','AA_Fortfuehrungsauftrag','AX_Einrichtungsauftrag')]"
            + " return if($adv) then $adv/*:koordinatenangaben/*[xs:boolean(*:standard)]/*:crs/data(@xlink:href)"
            + " else $ancestors[*:boundedBy/*:Envelope/@srsName][1]/*:boundedBy/*:Envelope/data(@srsName)";

    // Byte comparison
    private static final byte[] srsNameB = new String("srsName").getBytes();

    protected final IIGeometryFactory geometryFactory;
    protected com.vividsolutions.jts.geom.GeometryFactory jtsFactory;
    protected final GmlGeoXUtils geoutils;

    private final Set<String> gmlGeometries = new TreeSet<String>();

    private String standardSRS = null;

    private static final boolean debug = LOGGER.isDebugEnabled();
    private static final Set<String> LOGGED_UNKNOWN_CRS = new HashSet<>();

    private GeometryManager mgr = new GeometryManager();

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
            Thread.currentThread()
                    .setContextClassLoader(getClass().getClassLoader());
            if (CRSManager.get("default") == null || CRSManager.get("default")
                    .getCRSByCode(CRSCodeType.valueOf(
                            "http://www.opengis.net/def/crs/EPSG/0/5555")) == null) {
                loadGmlGeoXSrsConfiguration();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }

        this.geometryFactory = new IIGeometryFactory();
        this.jtsFactory = new com.vividsolutions.jts.geom.GeometryFactory();
        this.geoutils = new GmlGeoXUtils(this, this.geometryFactory,
                this.jtsFactory);
    }

    private void loadGmlGeoXSrsConfiguration() throws QueryException {
        final String srsConfigDirPath = PropertyUtils
                .getenvOrProperty(ETF_GMLGEOX_SRSCONFIG_DIR, null);
        final CRSManager crsMgr = new CRSManager();
        /* If the configuration for EPSG 5555 can be accessed, the CRSManger is already configured by the test driver. */
        if (srsConfigDirPath != null) {
            final IFile srsConfigDirectory = new IFile(srsConfigDirPath,
                    ETF_GMLGEOX_SRSCONFIG_DIR);

            try {
                srsConfigDirectory.expectDirIsWritable();
                crsMgr.init(srsConfigDirectory);
            } catch (Exception e) {
                throw new QueryException(
                        "Could not load SRS configuration files from directory referenced from GmlGeoX property '"
                                + ETF_GMLGEOX_SRSCONFIG_DIR
                                + "'. Reference is: " + srsConfigDirPath
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

                IoUtils.copyResourceToFile(this, "/srsconfig/default.xml",
                        new IFile(tempDir, "default.xml"));
                IoUtils.copyResourceToFile(this,
                        "/srsconfig/deegree/d3/config/ntv2/beta2007.gsb",
                        new IFile(tempDir,
                                "deegree/d3/config/ntv2/beta2007.gsb"));
                IoUtils.copyResourceToFile(this,
                        "/srsconfig/deegree/d3/parser-files.xml",
                        new IFile(tempDir, "deegree/d3/parser-files.xml"));
                IoUtils.copyResourceToFile(this,
                        "/srsconfig/deegree/d3/config/crs-definitions.xml",
                        new IFile(tempDir,
                                "deegree/d3/config/crs-definitions.xml"));
                IoUtils.copyResourceToFile(this,
                        "/srsconfig/deegree/d3/config/datum-definitions.xml",
                        new IFile(tempDir,
                                "deegree/d3/config/datum-definitions.xml"));
                IoUtils.copyResourceToFile(this,
                        "/srsconfig/deegree/d3/config/ellipsoid-definitions.xml",
                        new IFile(tempDir,
                                "deegree/d3/config/ellipsoid-definitions.xml"));
                IoUtils.copyResourceToFile(this,
                        "/srsconfig/deegree/d3/config/pm-definitions.xml",
                        new IFile(tempDir,
                                "deegree/d3/config/pm-definitions.xml"));
                IoUtils.copyResourceToFile(this,
                        "/srsconfig/deegree/d3/config/projection-definitions.xml",
                        new IFile(tempDir,
                                "deegree/d3/config/projection-definitions.xml"));
                IoUtils.copyResourceToFile(this,
                        "/srsconfig/deegree/d3/config/transformation-definitions.xml",
                        new IFile(tempDir,
                                "deegree/d3/config/transformation-definitions.xml"));

                crsMgr.init(tempDir);
            } catch (IOException e) {
                throw new QueryException(
                        "Exception occurred while extracting the SRS configuration files provided by GmlGeoX to a temporary "
                                + "directory and loading them from there. Exception message is: "
                                + e.getMessage());
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
    public void configureSpatialReferenceSystems(
            final String configurationDirectoryPathName) throws QueryException {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            final File configurationDirectory = new File(
                    configurationDirectoryPathName);
            if (!configurationDirectory.exists()
                    || !configurationDirectory.isDirectory()
                    || !configurationDirectory.canRead()) {
                throw new IllegalArgumentException(
                        "Given path name does not identify a directory that exists and that can be read.");
            } else {
                Thread.currentThread()
                        .setContextClassLoader(getClass().getClassLoader());
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
     * @return a sequence of JTS geometry objects that are not collection types; can be empty but not null
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
                    geoms.addAll(Arrays.asList(flattenAllGeometryCollections(
                            col.getGeometryN(i))));
                }

            } else {
                geoms = Collections.singletonList(geom);
            }

            return geoms.toArray(
                    new com.vividsolutions.jts.geom.Geometry[geoms.size()]);
        }
    }

    /**
     * Identifies points of the given line, where the segment that ends in a point and the following segment that starts with that point form a change in direction whose angular value is within a given interval.
     *
     * @param geom
     *            A LineString which shall be checked for directional changes whose value is within the given interval.
     * @param minAngle
     *            Minimum directional change to be considered, in degrees. 0<=minAngle<=maxAngle<=180
     * @param maxAngle
     *            Maximum directional change to be considered, in degrees. 0<=minAngle<=maxAngle<=180
     * @return The point(s) where the line has a directional change within the given change interval. Can be <code>null</code> in case that the given geometry is <code>null</code> or only has one segment.
     * @throws QueryException
     *             If the given geometry is not a LineString, or the minimum and maximum values are incorrect.
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Point[] directionChanges(
            com.vividsolutions.jts.geom.Geometry geom, Object minAngle,
            Object maxAngle) throws QueryException {

        if (geom == null) {

            return null;

        } else if (!(geom instanceof LineString
                || geom instanceof MultiLineString)) {
            throw new QueryException("Geometry must be a LineString. Found: "
                    + geom.getClass().getName());
        } else {

            double min;
            double max;

            if (minAngle instanceof Number && maxAngle instanceof Number) {

                min = ((Number) minAngle).doubleValue();
                max = ((Number) maxAngle).doubleValue();

                if (min < 0 || max < 0 || min > 180 || max > 180 || min > max) {
                    throw new QueryException(
                            "0 <= minAngle <= maxAngle <= 180. Found minAngle '"
                                    + minAngle + "', maxAngle '" + maxAngle
                                    + "'.");
                }
            } else {
                throw new QueryException(
                        "minAngle and maxAngle must be numbers");
            }

            Coordinate[] coords = geom.getCoordinates();

            if (coords.length < 3) {

                return null;

            } else {

                GeometryFactory fac = geom.getFactory();

                List<com.vividsolutions.jts.geom.Point> result = new ArrayList<>();

                for (int i = 0; i < coords.length - 2; i++) {

                    Coordinate coord1 = coords[i];
                    Coordinate coord2 = coords[i + 1];
                    Coordinate coord3 = coords[i + 2];

                    double angleVector1to2 = Angle.angle(coord1, coord2);
                    double angleVector2to3 = Angle.angle(coord2, coord3);

                    double diff_rad = Angle.diff(angleVector1to2,
                            angleVector2to3);

                    double diff_deg = Angle.toDegrees(diff_rad);

                    if (diff_deg >= min && diff_deg <= max) {
                        com.vividsolutions.jts.geom.Point p = fac
                                .createPoint(coord2);
                        result.add(p);
                    }
                }

                if (result.isEmpty()) {
                    return null;
                } else {
                    return result.toArray(
                            new com.vividsolutions.jts.geom.Point[result
                                    .size()]);
                }
            }
        }
    }

    /**
     * Identifies points of the given line, where the segment that ends in a point and the following segment that starts with that point form a change in direction whose angular value is greater than the given limit.
     *
     * @param geom
     *            A LineString which shall be checked for directional changes that are greater than the given limit.
     * @param limitAngle
     *            Angular value of directional change that defines the limit, in degrees. 0 <= limitAngle <= 180
     * @return The point(s) where the line has a directional change that is greater than the given limit. Can be <code>null</code> in case that the given geometry is <code>null</code> or only has one segment.
     * @throws QueryException
     *             If the given geometry is not a LineString, or the limit value is incorrect.
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Point[] directionChangesGreaterThanLimit(
            com.vividsolutions.jts.geom.Geometry geom, Object limitAngle)
            throws QueryException {

        if (geom == null) {

            return null;

        } else if (!(geom instanceof LineString
                || geom instanceof MultiLineString)) {
            throw new QueryException("Geometry must be a LineString. Found: "
                    + geom.getClass().getName());
        } else {

            double limit;

            if (limitAngle instanceof Number) {

                limit = ((Number) limitAngle).doubleValue();

                if (limit < 0 || limit > 180) {
                    throw new QueryException(
                            "0 <= limitAngle <= 180. Found limitAngle '"
                                    + limitAngle + "'.");
                }
            } else {
                throw new QueryException("limitAngle must be a number");
            }

            Coordinate[] coords = geom.getCoordinates();

            if (coords.length < 3) {

                return null;

            } else {

                GeometryFactory fac = geom.getFactory();

                List<com.vividsolutions.jts.geom.Point> result = new ArrayList<>();

                for (int i = 0; i < coords.length - 2; i++) {

                    Coordinate coord1 = coords[i];
                    Coordinate coord2 = coords[i + 1];
                    Coordinate coord3 = coords[i + 2];

                    double angleVector1to2 = Angle.angle(coord1, coord2);
                    double angleVector2to3 = Angle.angle(coord2, coord3);

                    double diff_rad = Angle.diff(angleVector1to2,
                            angleVector2to3);

                    double diff_deg = Angle.toDegrees(diff_rad);

                    if (diff_deg > limit) {
                        com.vividsolutions.jts.geom.Point p = fac
                                .createPoint(coord2);
                        result.add(p);
                    }
                }

                if (result.isEmpty()) {
                    return null;
                } else {
                    return result.toArray(
                            new com.vividsolutions.jts.geom.Point[result
                                    .size()]);
                }
            }
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
    public Element validateAndReport(ANode node, String testMask)
            throws QueryException {

        ValidationReport vr = this.executeValidate(node, testMask);

        DocumentBuilderFactory docFactory = DocumentBuilderFactory
                .newInstance();
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
     * The validation tasks to perform can be specified via the given mask. The mask is a simple string, where the character '1' at the position of a specific test (assuming a 1-based index) specifies that the test shall be performed. If the mask does not contain a character at the position of a specific test (because the mask is empty or the length is smaller than the position), then the test will NOT be executed. If no mask is provided, ALL tests will be executed.
     * <p>
     * The following tests are available:
     * <p>
     * <table border="1">
     * <tr>
     * <th>Position</th>
     * <th>Test Name</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td>General Validation</td>
     * <td>This test validates the given geometry using the validation functionality of both deegree and JTS. More specifically:
     * <p>
     * <p>
     * <span style="text-decoration: underline;"><strong>deegree based validation:</strong></span>
     * </p>
     * <ul>
     * <li>primitive geometry (point, curve, ring, surface):
     * <ul>
     * <li>point: no specific validation</li>
     * <li>curve:
     * <ul>
     * <li>duplication of successive control points (only for linear curve segments)</li>
     * <li>segment discontinuity</li>
     * <li>self intersection (based on JTS isSimple())</li>
     * </ul>
     * </li>
     * <li>ring:
     * <ul>
     * <li>Same as curve.</li>
     * <li>In addition, test if ring is closed</li>
     * </ul>
     * </li>
     * <li>surface:
     * <ul>
     * <li>only checks PolygonPatch, individually:</li>
     * <li>applies ring validation to interior and exterior rings</li>
     * <li>checks ring orientation (ignored for GML 3.1):
     * <ul>
     * <li>must be counter-clockwise for exterior ring</li>
     * <li>must be clockwise for interior ring</li>
     * </ul>
     * </li>
     * <li>interior ring intersects exterior</li>
     * <li>interior ring outside of exterior ring</li>
     * <li>interior rings intersection</li>
     * <li>interior rings are nested</li>
     * </ul>
     * </li>
     * </ul>
     * </li>
     * <li>composite geometry: member geometries are validated individually</li>
     * <li>multi geometry: member geometries are validated individually</li>
     * </ul>
     * <p>
     * NOTE: There's some overlap with JTS validation. The following invalid situations are reported by the JTS validation:
     * </p>
     * <ul>
     * <li>curve self intersection</li>
     * <li>interior ring intersects exterior</li>
     * <li>interior ring outside of exterior ring</li>
     * <li>interior rings intersection</li>
     * <li>interior rings are nested</li>
     * <li>interior rings touch</li>
     * <li>interior ring touches exterior</li>
     * </ul>
     * <p>
     * <span style="text-decoration: underline;"><strong>JTS based validation</strong></span>:
     * </p>
     * <ul>
     * <li>Point:
     * <ul>
     * <li>invalid coordinates</li>
     * </ul>
     * </li>
     * <li>LineString:
     * <ul>
     * <li>invalid coordinates</li>
     * <li>too few points</li>
     * </ul>
     * </li>
     * <li>LinearRing:
     * <ul>
     * <li>invalid coordinates</li>
     * <li>closed ring</li>
     * <li>too few points</li>
     * <li>no self intersecting rings</li>
     * </ul>
     * </li>
     * <li>Polygon
     * <ul>
     * <li>invalid coordinates</li>
     * <li>closed ring</li>
     * <li>too few points</li>
     * <li>consistent area</li>
     * <li>no self intersecting rings</li>
     * <li>holes in shell</li>
     * <li>holes not nested</li>
     * <li>connected interiors</li>
     * </ul>
     * </li>
     * <li>MultiPoint:
     * <ul>
     * <li>invalid coordinates</li>
     * </ul>
     * </li>
     * <li>MultiLineString:
     * <ul>
     * <li>Each contained LineString is validated on its own.</li>
     * </ul>
     * </li>
     * <li>MultiPolygon:
     * <ul>
     * <li>Per polygon:
     * <ul>
     * <li>invalid coordinates</li>
     * <li>closed ring</li>
     * <li>holes in shell</li>
     * <li>holes not nested</li>
     * </ul>
     * </li>
     * <li>too few points</li>
     * <li>consistent area</li>
     * <li>no self intersecting rings</li>
     * <li>shells not nested</li>
     * <li>connected interiors</li>
     * </ul>
     * </li>
     * <li>GeometryCollection:
     * <ul>
     * <li>Each member of the collection is validated on its own.</li>
     * </ul>
     * </li>
     * </ul>
     * <p>
     * General description of checks performed by JTS:
     * </p>
     * <ul>
     * <li>invalid coordinates: x and y are neither NaN or infinite)</li>
     * <li>closed ring: tests if ring is closed; empty rings are closed by definition</li>
     * <li>too few points: tests if length of coordinate array - after repeated points have been removed - is big enough (e.g. &gt;= 4 for a ring, &gt;= 2 for a line string)</li>
     * <li>no self intersecting rings: Check that there is no ring which self-intersects (except of course at its endpoints); required by OGC topology rules</li>
     * <li>consistent area: Checks that the arrangement of edges in a polygonal geometry graph forms a consistent area. Includes check for duplicate rings.</li>
     * <li>holes in shell: Tests that each hole is inside the polygon shell (i.e. hole rings do not cross the shell ring).</li>
     * <li>holes not nested: Tests that no hole is nested inside another hole.</li>
     * <li>connected interiors: Check that the holes do not split the interior of the polygon into at least two pieces.</li>
     * <li>shells not nested: Tests that no element polygon is wholly in the interior of another element polygon (of a MultiPolygon).</li>
     * </ul>
     * </td>
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
     * <tr>
     * <td>4</td>
     * <td>isSimple</td>
     * <td>
     * <p>
     * Tests whether a geometry is simple, based on JTS Geometry.isSimple(). In general, the OGC Simple Features specification of simplicity follows the rule: A Geometry is simple if and only if the only self-intersections are at boundary points.
     * </p>
     * <p>
     * Simplicity is defined for each JTS geometry type as follows:
     * </p>
     * <ul>
     * <li>Polygonal geometries are simple if their rings are simple (i.e., their rings do not self-intersect).
     * <ul>
     * <li>Note: This does not check if different rings of the geometry intersect, meaning that isSimple cannot be used to fully test for (invalid) self-intersections in polygons. The JTS validity check fully tests for self-intersections in polygons, and is part of the general validation in GmlGeoX.</li>
     * </ul>
     * </li>
     * <li>Linear geometries are simple iff they do not self-intersect at points other than boundary points.</li>
     * <li>Zero-dimensional (point) geometries are simple if and only if they have no repeated points.</li>
     * <li>Empty geometries are always simple, by definition.</li>
     * </ul>
     * </td>
     * </tr>
     * </table>
     * <p>
     * Examples:
     * <ul>
     * <li>The mask '0100' indicates that only the 'Polygon Patch Connectivity' test shall be performed.</li>
     * <li>The mask '1110' indicates that all tests except the isSimple test shall be performed .</li>
     * </ul>
     *
     * @param node
     *            The GML geometry element to validate.
     * @param testMask
     *            Defines which tests shall be executed; if <code>null</code>, all tests will be executed.
     * @return a validation report, with the validation result and validation message (providing further details about any errors). The validation result is encoded as a sequence of characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVFF' shows that the first test was skipped, while the second test passed and the third and fourth failed.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    ValidationReport executeValidate(ANode node, String testMask)
            throws QueryException {

        try {

            // determine which tests to execute
            boolean isTestGeonovum, isTestPolygonPatchConnectivity,
                    isTestRepetitionInCurveSegments, isTestIsSimple;

            if (testMask == null) {

                isTestGeonovum = true;
                isTestPolygonPatchConnectivity = true;
                isTestRepetitionInCurveSegments = true;
                isTestIsSimple = true;

            } else {
                isTestGeonovum = testMask.length() >= 1
                        && testMask.charAt(0) == '1';
                isTestPolygonPatchConnectivity = testMask.length() >= 2
                        && testMask.charAt(1) == '1';
                isTestRepetitionInCurveSegments = testMask.length() >= 3
                        && testMask.charAt(2) == '1';
                isTestIsSimple = testMask.length() >= 4
                        && testMask.charAt(3) == '1';
            }

            boolean isValidGeonovum = false;
            boolean polygonPatchesAreConnected = false;
            boolean noRepetitionInCurveSegment = false;
            boolean isSimple = false;

            String srsName = determineSrsName(node);

            BXNode elem = node.toJava();

            List<ValidatorMessage> validationMessages = new ArrayList<ValidatorMessage>();
            // ================
            // Geonovum validation (deegree and JTS validation)

            if (isTestGeonovum) {

                ValidatorContext ctx = new ValidatorContext();

                GeometryElementHandler handler = new GeometryElementHandler(ctx,
                        null, srsName, this.geometryFactory);
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

            if (isTestPolygonPatchConnectivity
                    || isTestRepetitionInCurveSegments || isTestIsSimple) {

                ValidatorContext ctx = new ValidatorContext();
                SecondaryGeometryElementValidationHandler handler = new SecondaryGeometryElementValidationHandler(
                        isTestPolygonPatchConnectivity,
                        isTestRepetitionInCurveSegments, isTestIsSimple, ctx,
                        srsName, this.geoutils, this.geometryFactory);

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
                    polygonPatchesAreConnected = handler
                            .arePolygonPatchesConnected();
                }

                // ================
                // Test: point repetition in curve segment
                if (isTestRepetitionInCurveSegments) {
                    noRepetitionInCurveSegment = handler
                            .isNoRepetitionInCurveSegments();
                }

                // ================
                // Test: isSimple
                if (isTestIsSimple) {
                    isSimple = handler.isSimple();
                }

                if (!polygonPatchesAreConnected || !noRepetitionInCurveSegment
                        || !isSimple) {
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

            if (!isTestIsSimple) {
                sb.append("S");
            } else if (isSimple) {
                sb.append("V");
            } else {
                sb.append("F");
            }

            return new ValidationReport(sb.toString(), validationMessages);

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Tests if the first geometry contains the second geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geom1
     *            represents the first geometry, encoded as a GML geometry element
     * @param geom2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry contains the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean contains(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.CONTAINS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing contains(Value,Value). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry contains a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean contains(Value arg1, Value arg2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2,
                    SpatialRelOp.CONTAINS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing contains(Value,Value,boolean). Message is: "
                            + e.getMessage());

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
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.CONTAINS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing containsGeomGeom(Geometry,Geometry). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry contains a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean containsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.CONTAINS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing containsGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry crosses the second geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geom1
     *            represents the first geometry, encoded as a GML geometry element
     * @param geom2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry crosses the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean crosses(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.CROSSES);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing crosses(Value,Value). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry crosses a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean crosses(Value arg1, Value arg2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2,
                    SpatialRelOp.CROSSES, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing crosses(Value,Value,boolean). Message is: "
                            + e.getMessage());

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
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.CROSSES);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing crossesGeomGeom(Geometry,Geometry). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry crosses a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean crossesGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.CROSSES, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing crossesGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry equals the second geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geom1
     *            represents the first geometry, encoded as a GML geometry element
     * @param geom2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry equals the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean equals(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.EQUALS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing equals(Value,Value). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry equals a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean equals(Value arg1, Value arg2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2,
                    SpatialRelOp.EQUALS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing equals(Value,Value,boolean). Message is: "
                            + e.getMessage());

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
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.EQUALS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing equalsGeomGeom(Geometry,Geometry). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry equals a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean equalsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.EQUALS, matchAll);
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
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geom1
     *            represents the first geometry, encoded as a GML geometry element
     * @param geom2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry intersects the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersects(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.INTERSECTS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing intersects(Value,Value). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry intersects a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean intersects(Value arg1, Value arg2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2,
                    SpatialRelOp.INTERSECTS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing intersects(Value,Value,boolean). Message is: "
                            + e.getMessage());

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
    public boolean intersectsGeomGeom(
            com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.INTERSECTS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing intersectsGeomGeom(Geometry,Geometry). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry intersects a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersectsGeomGeom(
            com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.INTERSECTS, matchAll);
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
    public String determineSrsName(final ANode geometryNode)
            throws QueryException {

        byte[] srsDirect = geometryNode.attribute(srsNameB);

        if (srsDirect != null) {

            return new String(srsDirect);

        } else if (this.standardSRS != null) {

            return this.standardSRS;

        } else {

            Context ctx = queryContext.context;

            try (QueryProcessor qp = new QueryProcessor(SRS_SEARCH_QUERY,
                    ctx)) {
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
     * Determine the name of the SRS that applies to the given geometry component element (e.g. a curve segment). The SRS is looked up as follows (in order):
     *
     * <ol>
     * <li>If a standard SRS is defined (see {@link #setStandardSRS(String)}), it is used.</li>
     * <li>Otherwise, if an ancestor of the given element has the 'srsName' attribute, then the value of that attribute from the first ancestor (i.e., the nearest) is used.</li>
     * <li>Otherwise, if the root element of the document of the given element has local name 'AX_Bestandsdatenauszug', 'AX_NutzerbezogeneBestandsdatenaktualisierung_NBA', 'AA_Fortfuehrungsauftrag', or 'AX_Einrichtungsauftrag', then the standard CRS (within element 'koordinatenangaben') as defined by the AAA GeoInfoDok is used.</li>
     * <li>Otherwise, if an ancestor of the given element has a child element with local name 'boundedBy', which itself has a child with local name 'Envelope', and that child has an 'srsName' attribute, then the value of that attribute from the first ancestor (i.e., the nearest) that fulfills the criteria is used.</li>
     * </ol>
     *
     * NOTE: The underlying query is independent of a specific GML namespace.
     *
     * @param geometryComponentNode
     *            a gml geometry component node (e.g. Arc or Circle)
     * @return the value of the applicable 'srsName' attribute, if found, otherwise <code>null</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String determineSrsNameForGeometryComponent(
            final ANode geometryComponentNode) throws QueryException {

        if (this.standardSRS != null) {

            return this.standardSRS;

        } else {

            Context ctx = queryContext.context;

            try (QueryProcessor qp = new QueryProcessor(
                    SRS_SEARCH_QUERY_GEOMETRY_COMPONENT, ctx)) {
                // Bind to context:
                qp.bind("geomComp", geometryComponentNode);
                qp.context(geometryComponentNode);
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
    public com.vividsolutions.jts.geom.Geometry parseGeometry(Value v)
            throws QueryException {

        try {
            if (v instanceof ANode) {

                ANode node = (ANode) v;

                return geoutils.toJTSGeometry(node);

            } else if (v instanceof Jav && ((Jav) v)
                    .toJava() instanceof com.vividsolutions.jts.geom.Geometry) {

                return (com.vividsolutions.jts.geom.Geometry) ((Jav) v)
                        .toJava();

            } else {
                throw new IllegalArgumentException(
                        "First argument is neither a single node nor a JTS geometry.");
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
    private boolean applySpatialRelationshipOperation(Object geom1x,
            Object geom2x, SpatialRelOp op) throws QueryException {

        if (geom1x == null || geom2x == null || geom1x instanceof Empty
                || geom2x instanceof Empty) {
            return false;
        }

        if ((geom1x instanceof GeometryCollection
                && geoutils.isGeometryCollectionButNotASubtype(
                        (GeometryCollection) geom1x))

                || (geom2x instanceof GeometryCollection
                        && geoutils.isGeometryCollectionButNotASubtype(
                                (GeometryCollection) geom2x))) {

            if (geom1x instanceof GeometryCollection
                    && !((GeometryCollection) geom1x).isEmpty()) {
                throw new QueryException(
                        "First argument is a non-empty geometry collection. This is not supported by this method.");
            } else if (geom2x instanceof GeometryCollection
                    && !((GeometryCollection) geom2x).isEmpty()) {
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
                throw new IllegalArgumentException(
                        "Unknown spatial relationship operator: "
                                + op.toString());
            }

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception occurred while applying spatial relationship operation (with single geometry to compare). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    private boolean applySpatialRelationshipOperation(Object arg1, Object arg2,
            SpatialRelOp op, boolean matchAll) throws QueryException {

        try {

            if (arg1 == null || arg2 == null || arg1 instanceof Empty
                    || arg2 instanceof Empty
                    || (arg1 instanceof GeometryCollection
                            && ((GeometryCollection) arg1).isEmpty())
                    || (arg2 instanceof GeometryCollection
                            && ((GeometryCollection) arg2).isEmpty())) {

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
                List<com.vividsolutions.jts.geom.Geometry> gc = geoutils
                        .toFlattenedJTSGeometryCollection(geom);
                result.addAll(gc);
            } else {
                result.add(o);
            }
        }

        return result;
    }

    /**
     * Tests if the first and the second geometry are disjoint.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geom1
     *            represents the first geometry, encoded as a GML geometry element
     * @param geom2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first and the second geometry are disjoint, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjoint(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.ISDISJOINT);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isDisjoint(Value,Value). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry is disjoint with a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean isDisjoint(Value arg1, Value arg2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2,
                    SpatialRelOp.ISDISJOINT, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isDisjoint(Value,Value,boolean). Message is: "
                            + e.getMessage());

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
    public boolean isDisjointGeomGeom(
            com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2) throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.ISDISJOINT);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isDisjointGeomGeom(Geometry,Geometry). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry is disjoint with a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjointGeomGeom(
            com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.ISDISJOINT, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isDisjointGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry is within the second geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geom1
     *            represents the first geometry, encoded as a GML geometry element
     * @param geom2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry is within the second geometry, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithin(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.ISWITHIN);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isWithin(Value,Value). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry is within a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean isWithin(Value arg1, Value arg2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2,
                    SpatialRelOp.ISWITHIN, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isWithin(Value,Value,boolean). Message is: "
                            + e.getMessage());

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
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.ISWITHIN);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isWithinGeomGeom(Geometry,Geometry). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry is within a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithinGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.ISWITHIN, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing isWithinGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry overlaps the second geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geom1
     *            represents the first geometry, encoded as a GML geometry element
     * @param geom2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry overlaps the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlaps(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.OVERLAPS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing overlaps(Value,Value). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry overlaps a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean overlaps(Value arg1, Value arg2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2,
                    SpatialRelOp.OVERLAPS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing overlaps(Value,Value,boolean). Message is: "
                            + e.getMessage());

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
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.OVERLAPS);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing overlapsGeomGeom(Geometry,Geometry). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry overlaps a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlapsGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.OVERLAPS, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing overlapsGeomGeom(Geometry,Geometry,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if the first geometry touches the second geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geom1
     *            represents the first geometry, encoded as a GML geometry element
     * @param geom2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry touches the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean touches(ANode geom1, ANode geom2) throws QueryException {
        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.TOUCHES);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing touches(Value,Value). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry touches a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean touches(Value arg1, Value arg2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(arg1, arg2,
                    SpatialRelOp.TOUCHES, matchAll);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing touches(Value,Value,boolean). Message is: "
                            + e.getMessage());

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
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.TOUCHES);
        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing touchesGeomGeom(Geometry,Geometry). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    /**
     * Tests if one geometry touches a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     *
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean touchesGeomGeom(com.vividsolutions.jts.geom.Geometry geom1,
            com.vividsolutions.jts.geom.Geometry geom2, boolean matchAll)
            throws QueryException {

        try {
            return applySpatialRelationshipOperation(geom1, geom2,
                    SpatialRelOp.TOUCHES, matchAll);
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
     * Set the maximum number of points to be created when interpolating an arc. Default is 1000. The lower the maximum error (set via {@link #setMaxErrorForInterpolation(double)}), the higher the number of points needs to be. Arc interpolation will never create more than the configured maximum number of points. However, the interpolation will also never create more points than needed to achieve the maximum error. In order to achieve interpolations with a very low maximum error, the maximum number of points needs to be increased accordingly.
     *
     * @param maxNumPoints
     *            maximum number of points to be created when interpolating an arc
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void setMaxNumPointsForInterpolation(int maxNumPoints)
            throws QueryException {
        if (maxNumPoints <= 0) {
            throw new QueryException(
                    "Given parameter value must be greater than zero. Was: "
                            + maxNumPoints + ".");
        } else {
            this.geometryFactory.setMaxNumPoints(maxNumPoints);
        }
    }

    /**
     * Set the maximum error (e.g. 0.00000001 - default setting is 0.00001), i.e. the maximum difference between an arc and the interpolated line string - that shall be achieved when creating new arc interpolations. The lower the error (maximum difference), the more interpolation points will be needed. However, note that a maximum for the number of such points exists. It can be set via {@link #setMaxNumPointsForInterpolation(int)} (default value is stated in the documentation of that method).
     *
     * @param maxError
     *            the maximum difference between an arc and the interpolated line
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void setMaxErrorForInterpolation(double maxError)
            throws QueryException {
        if (maxError <= 0) {
            throw new QueryException(
                    "Given parameter value must be greater than zero. Was: "
                            + maxError + ".");
        } else {
            this.geometryFactory.setMaxError(maxError);
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
     * @param arg
     *            a single or collection of JTS geometries or geometry nodes.
     * @return the union of the geometries - can be a JTS geometry collection
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry unionGeom(
            com.vividsolutions.jts.geom.Geometry[] arg) throws QueryException {

        try {

            List<com.vividsolutions.jts.geom.Geometry> geoms = Arrays
                    .asList(arg);

            com.vividsolutions.jts.geom.GeometryCollection gc = geoutils
                    .toJTSGeometryCollection(geoms, true);

            return gc.union();

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Create the union of the given geometry nodes.
     *
     * @param val
     *            a single or collection of geometry nodes.
     * @return the union of the geometries - can be a JTS geometry collection
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry union(Value val)
            throws QueryException {

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
            com.vividsolutions.jts.geom.GeometryCollection unionsGc = geoutils
                    .toJTSGeometryCollection(unions, true);

            return unionsGc.union();

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception occurred while applying unionNodes(..)). Message is: "
                            + e.getMessage());
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
    public boolean isEmptyGeom(com.vividsolutions.jts.geom.Geometry geom) {

        if (geom == null || geom.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param arcStringNode
     *            A gml:Arc or gml:ArcString element
     * @return The coordinate of the second control point of the first invalid arc, or <code>null</code> if all arcs are valid.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public Coordinate checkSecondControlPointInMiddleThirdOfArc(
            ANode arcStringNode) throws QueryException {

        Coordinate[] controlPoints = parseArcStringControlPoints(arcStringNode);

        for (int i = 2; i < controlPoints.length; i = i + 2) {

            Coordinate c1 = controlPoints[i - 2];
            Coordinate c2 = controlPoints[i - 1];
            Coordinate c3 = controlPoints[i];

            Circle circle;
            try {
                circle = Circle.from3Coordinates(c1, c2, c3);
            } catch (InvalidCircleException e) {
                return c2;
            }
            Coordinate center = circle.center();

            double d12 = Angle.angleBetweenOriented(c1, center, c2);
            double d13 = Angle.angleBetweenOriented(c1, center, c3);

            boolean controlPointsOrientedClockwise;

            if (d12 >= 0 && d13 >= 0) {

                if (d12 > d13) {
                    // CW
                    controlPointsOrientedClockwise = true;
                } else {
                    // CCW
                    controlPointsOrientedClockwise = false;
                }
            } else if (d12 >= 0 && d13 < 0) {
                // CCW
                controlPointsOrientedClockwise = false;
            } else if (d12 < 0 && d13 >= 0) {
                // CW
                controlPointsOrientedClockwise = true;
            } else {
                // d12 < 0 && d13 < 0
                if (d12 < d13) {
                    // CCW
                    controlPointsOrientedClockwise = false;
                } else {
                    // CW
                    controlPointsOrientedClockwise = true;
                }
            }

            double fullAngle1to2;
            double fullAngle1to3;

            if (controlPointsOrientedClockwise) {
                if (d12 >= 0) {
                    fullAngle1to2 = Math.PI * 2 - d12;
                } else {
                    fullAngle1to2 = Math.abs(d12);
                }
                if (d13 >= 0) {
                    fullAngle1to3 = Math.PI * 2 - d13;
                } else {
                    fullAngle1to3 = Math.abs(d13);
                }
            } else {
                if (d12 >= 0) {
                    fullAngle1to2 = d12;
                } else {
                    fullAngle1to2 = Math.PI * 2 - Math.abs(d12);
                }
                if (d13 >= 0) {
                    fullAngle1to3 = d13;
                } else {
                    fullAngle1to3 = Math.PI * 2 - Math.abs(d13);
                }
            }

            double thirdOfFullAngle1to3 = fullAngle1to3 / 3;

            double middleThirdStart = thirdOfFullAngle1to3;
            double middleThirdEnd = fullAngle1to3 - thirdOfFullAngle1to3;

            if (middleThirdStart > fullAngle1to2
                    || middleThirdEnd < fullAngle1to2) {
                return c2;
            }
        }

        return null;
    }

    /**
     * @param circleNode
     *            A gml:Circle element, defined by three control points
     * @param minSeparationInDegree
     *            the minimum angle between each control point, in degree (0<=x<=120)
     * @return The coordinate of a control point which does not have the minimum angle to one of the other control points, or <code>null</code> if the angles between all points are greater than or equal to the minimum separation angle
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public Coordinate checkMinimumSeparationOfCircleControlPoints(
            ANode circleNode, Object minSeparationInDegree)
            throws QueryException {

        double minSepDeg;
        if (minSeparationInDegree instanceof Number) {
            minSepDeg = ((Number) minSeparationInDegree).doubleValue();
        } else {
            throw new QueryException(
                    "Parameter minSeparationInDegree must be a number.");
        }

        if (minSepDeg < 0 || minSepDeg > 120) {
            throw new QueryException(
                    "Invalid parameter minSeparationInDegree (must be >= 0 and <= 120).");
        }

        Coordinate[] controlPoints = parseArcStringControlPoints(circleNode);

        Coordinate c1 = controlPoints[0];
        Coordinate c2 = controlPoints[1];
        Coordinate c3 = controlPoints[2];

        Circle circle;
        try {
            circle = Circle.from3Coordinates(c1, c2, c3);
        } catch (InvalidCircleException e) {
            return c1;
        }
        Coordinate center = circle.center();

        // NOTE: angle in radians (0,PI)
        double d12 = Angle.angleBetween(c1, center, c2);
        double d13 = Angle.angleBetween(c1, center, c3);
        double d23 = Angle.angleBetween(c2, center, c3);

        double minSeparation = Angle.toRadians(minSepDeg);

        if (d12 < minSeparation) {
            return c1;
        } else if (d13 < minSeparation) {
            return c1;
        } else if (d23 < minSeparation) {
            return c2;
        } else {
            return null;
        }
    }

    private Coordinate[] parseArcStringControlPoints(ANode arcStringNode)
            throws QueryException {

        String srsName = determineSrsNameForGeometryComponent(arcStringNode);

        ICRS crs = CRSManager.get("default")
                .getCRSByCode(CRSCodeType.valueOf(srsName));

        int dimension;
        if (crs == null) {
            if (!LOGGED_UNKNOWN_CRS.contains(srsName)) {
                LOGGED_UNKNOWN_CRS.add(srsName);
                LOGGER.error("parseArcStringControlPoints: could not find SRS '"
                        + srsName
                        + "', which should be used to determine the SRS dimension. Using dimension 2 as fallback. Check if the SRS configuration contains the SRS. If not, add the SRS to the configuration.");
            }
            dimension = 2;
        } else {
            dimension = crs.getDimension();
        }

        if (dimension != 2 && dimension != 3) {
            throw new QueryException(
                    "SRS determined to be " + srsName + " with dimension "
                            + dimension + ". Expected dimension of 2 or 3.");
        }

        String positions = null;
        Iterator<ANode> childIter = arcStringNode.children().iterator();
        while (childIter.hasNext()) {
            ANode child = childIter.next();
            String name = new String(child.name());
            if (name.endsWith("posList")) {
                positions = child.toJava().getTextContent();
                break;
            }
        }

        String[] tmp = StringUtils.split(positions);

        if (tmp.length % dimension != 0) {
            throw new QueryException("SRS determined to be " + srsName
                    + " with dimension " + dimension
                    + ". Number of coordinates does not match. Found "
                    + tmp.length + " coordinates in: " + positions);
        }

        int numberOfPoints = tmp.length / dimension;

        if (numberOfPoints < 3 || numberOfPoints % 2 != 1) {
            throw new QueryException("Found " + numberOfPoints
                    + " points. Expected three or a bigger, uneven number of points.");
        }

        double[] coordinates = new double[tmp.length];
        for (int i = 0; i < tmp.length; i++) {
            coordinates[i] = Double.parseDouble(tmp[i]);
        }

        Coordinate[] points = new Coordinate[numberOfPoints];
        for (int i = 0; i < numberOfPoints; i++) {
            int index = i * dimension;
            Coordinate coord;
            if (dimension == 2) {
                coord = new Coordinate(coordinates[index],
                        coordinates[index + 1]);
            } else {
                // dimension == 3
                coord = new Coordinate(coordinates[index],
                        coordinates[index + 1], coordinates[index + 2]);
            }
            points[i] = coord;
        }

        return points;
    }

    /**
     * Checks if a given geometry is closed. Only LineStrings and MultiLineStrings are checked.
     *
     * NOTE: Invokes the {@link #isClosedGeom(com.vividsolutions.jts.geom.Geometry, boolean)} method, with <code>true</code> for the second parameter.
     *
     * @see #isClosedGeom(com.vividsolutions.jts.geom.Geometry, boolean)
     * @param geom
     *            the geometry to check
     * @return <code>true</code>, if the geometry is closed, else <code>false</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosedGeom(com.vividsolutions.jts.geom.Geometry geom)
            throws QueryException {

        return isClosedGeom(geom, true);
    }

    /**
     * Checks if the geometry represented by the given node is closed. Only LineStrings and MultiLineStrings are checked.
     *
     * NOTE: Invokes the {@link #isClosed(ANode, boolean)} method, with <code>true</code> for the second parameter.
     *
     * @see #isClosed(ANode, boolean)
     * @param geom
     *            the geometry to check
     * @return <code>true</code>, if the geometry is closed, else <code>false</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosed(ANode geom) throws QueryException {
        return isClosed(geom, true);
    }

    /**
     * Checks if a given geometry is closed. Points and MultiPoints are closed by definition (they do not have a boundary). Polygons and MultiPolygons are never closed in 2D, and since operations in 3D are not supported, this method will always return <code>false</code> if a polygon is encountered - unless the parameter onlyCheckCurveGeometries is set to <code>true</code>. LinearRings are closed by definition. The remaining geometry types that will be checked are LineString and MultiLineString. If a (Multi)LineString is not closed, this method will return <code>false</code>.
     *
     * @param geom
     *            the geometry to test
     * @param onlyCheckCurveGeometries
     *            <code>true</code> if only curve geometries (i.e., for JTS: LineString, LinearRing, and MultiLineString) shall be tested, else <code>false</code> (in this case, the occurrence of polygons will result in the return value <code>false</code>).
     * @return <code>true</code> if the given geometry is closed, else <code>false</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosedGeom(com.vividsolutions.jts.geom.Geometry geom,
            boolean onlyCheckCurveGeometries) throws QueryException {

        try {

            List<com.vividsolutions.jts.geom.Geometry> gc = geoutils
                    .toFlattenedJTSGeometryCollection(geom);

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
                    throw new Exception("Unexpected geometry type encountered: "
                            + g.getClass().getName());
                }
            }

            // all relevant geometries are closed
            return true;

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Checks if the geometry represented by the given node is closed. Points and MultiPoints are closed by definition (they do not have a boundary). Polygons and MultiPolygons are never closed in 2D, and since operations in 3D are not supported, this method will always return <code>false</code> if a polygon is encountered - unless the parameter onlyCheckCurveGeometries is set to <code>true</code>. LinearRings are closed by definition. The remaining geometry types that will be checked are LineString and MultiLineString. If a (Multi)LineString is not closed, this method will return <code>false</code>.
     *
     * @param geomNode
     *            the geometry node to test
     * @param onlyCheckCurveGeometries
     *            <code>true</code> if only curve geometries (i.e., for JTS: LineString, LinearRing, and MultiLineString) shall be tested, else <code>false</code> (in this case, the occurrence of polygons will result in the return value <code>false</code>).
     * @return <code>true</code> if the geometry represented by the given node is closed, else <code>false</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosed(ANode geomNode, boolean onlyCheckCurveGeometries)
            throws QueryException {

        com.vividsolutions.jts.geom.Geometry geom = getOrCacheGeometry(
                geomNode);

        return isClosedGeom(geom, onlyCheckCurveGeometries);
    }

    /**
     * Identifies the holes contained in the geometry represented by the given geometry node and returns them as a JTS geometry. If holes were found a union is built, to ensure that the result is a valid JTS Polygon or JTS MultiPolygon. If no holes were found an empty JTS GeometryCollection is returned.
     *
     * @param geometryNode
     *            potentially existing holes will be extracted from the geometry represented by this node (the geometry can be a Polygon, MultiPolygon, or any other JTS geometry)
     * @return A geometry (JTS Polygon or MultiPolygon) with the holes contained in the given geometry. Can also be an empty JTS GeometryCollection but not <code>null</code>;
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry holes(ANode geometryNode)
            throws QueryException {

        com.vividsolutions.jts.geom.Geometry geom = getOrCacheGeometry(
                geometryNode);
        return holesGeom(geom);
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
    public com.vividsolutions.jts.geom.Geometry holesGeom(
            com.vividsolutions.jts.geom.Geometry geom) {

        if (isEmptyGeom(geom)) {

            return geoutils.emptyJTSGeometry();

        } else {

            List<com.vividsolutions.jts.geom.Geometry> holes = computeHoles(
                    geom);

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
    public com.vividsolutions.jts.geom.Geometry holesAsGeometryCollection(
            com.vividsolutions.jts.geom.Geometry geom) {

        if (isEmptyGeom(geom)) {

            return geoutils.emptyJTSGeometry();

        } else {

            List<com.vividsolutions.jts.geom.Geometry> holes = computeHoles(
                    geom);

            if (holes.isEmpty()) {
                return geoutils.emptyJTSGeometry();
            } else {
                return geoutils.toJTSGeometryCollection(holes, true);
            }
        }
    }

    protected List<com.vividsolutions.jts.geom.Geometry> computeHoles(
            com.vividsolutions.jts.geom.Geometry geom) {

        List<com.vividsolutions.jts.geom.Geometry> holes = new ArrayList<>();

        List<com.vividsolutions.jts.geom.Polygon> extractedPolygons = new ArrayList<>();

        GeometryExtracter.extract(geom,
                com.vividsolutions.jts.geom.Polygon.class, extractedPolygons);

        if (!extractedPolygons.isEmpty()) {

            // get holes as polygons

            for (com.vividsolutions.jts.geom.Polygon polygon : extractedPolygons) {

                // check that polygon has holes
                if (polygon.getNumInteriorRing() > 0) {

                    // for each hole, convert it to a polygon
                    for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                        com.vividsolutions.jts.geom.LineString ls = polygon
                                .getInteriorRingN(i);
                        com.vividsolutions.jts.geom.Polygon holeAsPolygon = geoutils
                                .toJTSPolygon(ls);
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
     * @param geometryNode
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
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean relate(ANode arg1, ANode arg2, String intersectionPattern)
            throws QueryException {

        try {

            checkIntersectionPattern(intersectionPattern);
            return applyRelate(arg1, arg2, intersectionPattern);

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing relate(Value,Value,String). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    private boolean applyRelate(Object geom1x, Object geom2x,
            String intersectionPattern) throws QueryException {

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
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public boolean relate(Value arg1, Value arg2, String intersectionPattern,
            boolean matchAll) throws QueryException {

        try {

            checkIntersectionPattern(intersectionPattern);
            return applyRelate(arg1, arg2, intersectionPattern, matchAll);

        } catch (Exception e) {
            QueryException qe = new QueryException(
                    "Exception while executing relate(Value,Value,String,boolean). Message is: "
                            + e.getMessage());

            throw qe;
        }
    }

    private boolean applyRelate(Object arg1, Object arg2,
            String intersectionPattern, boolean matchAll)
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
            com.vividsolutions.jts.geom.Geometry geom2,
            String intersectionPattern) throws QueryException {

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
     * @param geom1
     *            represents the first geometry, encoded as a JTS geometry object
     * @param geom2
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
            com.vividsolutions.jts.geom.Geometry geom2,
            String intersectionPattern, boolean matchAll)
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

    private void checkIntersectionPattern(String intersectionPattern)
            throws QueryException {
        if (intersectionPattern == null) {
            throw new QueryException("intersectionPattern is null.");
        } else {
            final Matcher m = INTERSECTIONPATTERN
                    .matcher(intersectionPattern.trim());
            if (!m.matches()) {
                throw new QueryException(
                        "intersectionPattern does not match the regular expression, which is: [0-2\\\\*TF]{9}");
            }
        }

    }

    /**
     * Computes the intersection between the first and the second geometry node.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public com.vividsolutions.jts.geom.Geometry intersection(
            final ANode geometry1, final ANode geometry2)
            throws QueryException {
        try {

            final com.vividsolutions.jts.geom.Geometry geom1 = getOrCacheGeometry(
                    geometry1);
            final com.vividsolutions.jts.geom.Geometry geom2 = getOrCacheGeometry(
                    geometry2);
            return geom1.intersection(geom2);

        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * Computes the intersection between the first and the second geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry1
     *            the first geometry
     * @param geometry2
     *            the second geometry
     * @return the point-set common to the two geometries
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry intersectionGeomGeom(
            final com.vividsolutions.jts.geom.Geometry geometry1,
            final com.vividsolutions.jts.geom.Geometry geometry2)
            throws QueryException {

        try {

            return geometry1.intersection(geometry2);

        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * Computes the difference between the first and the second geometry node.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
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
    public com.vividsolutions.jts.geom.Geometry difference(
            final ANode geometry1, final ANode geometry2)
            throws QueryException {
        try {

            final com.vividsolutions.jts.geom.Geometry geom1 = getOrCacheGeometry(
                    geometry1);
            final com.vividsolutions.jts.geom.Geometry geom2 = getOrCacheGeometry(
                    geometry2);
            return geom1.difference(geom2);

        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * @see #boundaryGeom(com.vividsolutions.jts.geom.Geometry)
     * @param geometryNode
     * @return the closure of the combinatorial boundary of this Geometry
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry boundary(
            final ANode geometryNode) throws QueryException {

        return boundaryGeom(getOrCacheGeometry(geometryNode));
    }

    /**
     * Returns the boundary, or an empty geometry of appropriate dimension if the given geometry is empty.(In the case of zero-dimensional geometries, 'an empty GeometryCollection is returned.) For a discussion of this function, see the OpenGIS SimpleFeatures Specification. As stated in SFS Section 2.1.13.1, "the boundary of a Geometry is a set of Geometries of the next lower dimension."
     *
     * @param geometry
     * @return the closure of the combinatorial boundary of this Geometry
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry boundaryGeom(
            final com.vividsolutions.jts.geom.Geometry geometry)
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
    public com.vividsolutions.jts.geom.Geometry differenceGeomGeom(
            final com.vividsolutions.jts.geom.Geometry geometry1,
            final com.vividsolutions.jts.geom.Geometry geometry2)
            throws QueryException {
        try {

            return geometry1.difference(geometry2);

        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * Computes the envelope of a geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometryNode
     *            represents the geometry
     * @return The bounding box, an array { x1, y1, x2, y2 }
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public Object[] envelope(ANode geometryNode) throws QueryException {

        /* Try lookup in envelope map first. */
        final DBNodeEntry geometryNodeEntry = new DBNodeEntry(
                (DBNode) geometryNode);

        if (mgr.hasEnvelope(geometryNodeEntry)) {

            Envelope env = mgr.getEnvelope(geometryNodeEntry);
            final Object[] res = {env.getMinX(), env.getMinY(), env.getMaxX(),
                    env.getMaxY()};
            return res;

        } else {

            /* Get JTS geometry and cache the envelope. */
            com.vividsolutions.jts.geom.Geometry geom = getOrCacheGeometry(
                    geometryNode);
            mgr.addEnvelope(geometryNodeEntry, geom.getEnvelopeInternal());
            return envelopeGeom(geom);
        }
    }

    /**
     * Computes the envelope of a geometry.
     *
     * @param geometry
     *            the geometry
     * @return The bounding box, as an array { x1, y1, x2, y2 }
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public Object[] envelopeGeom(com.vividsolutions.jts.geom.Geometry geometry)
            throws QueryException {
        try {
            final Envelope env = geometry.getEnvelopeInternal();
            final Object[] res = {env.getMinX(), env.getMinY(), env.getMaxX(),
                    env.getMaxY()};
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
    public DBNode[] search(Object minx, Object miny, Object maxx, Object maxy)
            throws QueryException {
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
    public DBNode[] search(final String indexName, Object minx, Object miny,
            Object maxx, Object maxy) throws QueryException {

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

    private DBNode[] performSearch(String indexName, double x1, double y1,
            double x2, double y2) throws QueryException {

        Iterable<DBNodeEntry> iter = mgr.search(indexName,
                Geometries.rectangle(x1, y1, x2, y2));
        List<DBNode> nodelist = new ArrayList<DBNode>();
        if (iter != null) {
            for (DBNodeEntry entry : iter) {
                Data d = queryContext.resources.database(entry.dbname,
                        new InputInfo("xpath", 0, 0));
                DBNode n = new DBNode(d, entry.pre, entry.nodeKind);
                if (n != null)
                    nodelist.add(n);
            }
        }

        if (debug && ++count % 5000 == 0) {
            String debugIndexName = indexName != null ? indexName : "default";
            logMemUsage("GmlGeoX#search " + count + ". Box: (" + x1 + ", " + y1
                    + ") (" + x2 + ", " + y2 + ")" + ". Hits: "
                    + nodelist.size() + " (in index '" + debugIndexName + "')");
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
    public DBNode[] search(final String indexName, ANode geometryNode)
            throws QueryException {

        /* Try lookup in envelope map first. */
        final DBNodeEntry entry = new DBNodeEntry((DBNode) geometryNode);

        if (mgr.hasEnvelope(entry)) {

            return search(indexName, mgr.getEnvelope(entry));

        } else {

            /* Get JTS geometry and cache the envelope. */
            com.vividsolutions.jts.geom.Geometry geom = getOrCacheGeometry(
                    geometryNode);

            if (geom.isEmpty()) {
                throw new QueryException(
                        "Geometry determined for the given node is empty "
                                + "(ensure that the given node is a geometry node that represents a non-empty geometry). "
                                + "Cannot perform a search based upon an empty geometry.");
            }

            mgr.addEnvelope(entry, geom.getEnvelopeInternal());

            return searchGeom(indexName, geom);
        }
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
    public DBNode[] searchGeom(com.vividsolutions.jts.geom.Geometry geom)
            throws QueryException {
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
    public DBNode[] searchGeom(final String indexName,
            com.vividsolutions.jts.geom.Geometry geom) throws QueryException {

        if (geom.isEmpty()) {
            throw new QueryException(
                    "Geometry is empty. Cannot perform a search based upon an empty geometry.");
        }

        return search(indexName, geom.getEnvelopeInternal());
    }

    private DBNode[] search(String indexName, Envelope env)
            throws QueryException {

        try {

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
    public DBNode[] searchInIndex(final String indexName)
            throws QueryException {

        try {
            logMemUsage("GmlGeoX#search.start " + count + ".");

            Iterable<DBNodeEntry> iter = mgr.search(indexName);
            List<DBNode> nodelist = new ArrayList<DBNode>();
            if (iter != null) {
                for (DBNodeEntry entry : iter) {
                    Data d = queryContext.resources.database(entry.dbname,
                            new InputInfo("xpath", 0, 0));
                    DBNode n = new DBNode(d, entry.pre, entry.nodeKind);
                    if (n != null)
                        nodelist.add(n);
                }
            }

            String debugIndexName = indexName != null ? indexName : "default";
            logMemUsage("GmlGeoX#search " + count + ". Hits: " + nodelist.size()
                    + " (in index '" + debugIndexName + "')");

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
            LOGGER.debug(progress + ". Memory: "
                    + Math.round(
                            memory.getHeapMemoryUsage().getUsed() / 1048576)
                    + " MB of "
                    + Math.round(memory.getHeapMemoryUsage().getMax() / 1048576)
                    + " MB.");
        }
    }

    /**
     * Set cache size for geometries. The cache will be reset.
     *
     * @param size
     *            the size of the geometry cache; default is 100000
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void cacheSize(Object size) throws QueryException {

        int newSize = 0;

        if (size instanceof BigInteger) {
            newSize = ((BigInteger) size).intValue();
        } else if (size instanceof Integer) {
            newSize = ((Integer) size).intValue();
        } else {
            throw new QueryException(
                    "Unsupported parameter type: " + size.getClass().getName());
        }

        mgr.resetCache(newSize);
    }

    /**
     * Get the current size of the geometry cache.
     *
     * @return the size of the geometry cache
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public int getCacheSize() throws QueryException {
        return mgr.getCacheSize();
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
    public void index(final ANode node, final ANode geometry)
            throws QueryException {
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
    public void index(final String indexName, final ANode node,
            final ANode geometry) throws QueryException {

        if (node instanceof DBNode && geometry instanceof DBNode) {

            try {

                final com.vividsolutions.jts.geom.Geometry _geom = getOrCacheGeometry(
                        geometry);
                final Envelope env = _geom.getEnvelopeInternal();

                if (!env.isNull()) {

                    final DBNodeEntry nodeEntry = new DBNodeEntry(
                            (DBNode) node);
                    if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {
                        mgr.index(indexName, nodeEntry,
                                Geometries.point(env.getMinX(), env.getMinY()));
                    } else {
                        mgr.index(indexName, nodeEntry,
                                Geometries.rectangle(env.getMinX(),
                                        env.getMinY(), env.getMaxX(),
                                        env.getMaxY()));
                    }

                    // also cache the envelope
                    final DBNodeEntry geomNodeEntry = new DBNodeEntry(
                            (DBNode) geometry);
                    mgr.addEnvelope(geomNodeEntry, env);
                }

                if (debug && mgr.indexSize(indexName) % 5000 == 0) {
                    String debugIndexName = indexName != null ? indexName
                            : "default";
                    logMemUsage("GmlGeoX#index progress (for index '"
                            + debugIndexName + "'): "
                            + mgr.indexSize(indexName));
                }

            } catch (final Exception e) {
                if (e instanceof XMLParsingException) {
                    // otherwise the stacktrace "<< is empty >>" is included
                    throw new QueryException(prepareXMLParsingException(
                            (XMLParsingException) e));
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
     * @param geometry
     *            The geometry whose points are checked to see if one of them has coordinates equal to that of {@code point}
     * @return <code>true</code> if the coordinates of the given {@code point} are equal to the coordinates of one of the points that define {@code geometry}, else <code>false</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean pointCoordInGeometryCoords(
            com.vividsolutions.jts.geom.Point point,
            com.vividsolutions.jts.geom.Geometry geometry)
            throws QueryException {

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
     * Checks two geometries for interior intersection of curve components. If both geometries are point based, the result will be <code>null</code> (since then there are no curves to check). Components of the first geometry are compared with the components of the second geometry (using a spatial index to prevent unnecessary checks): If two components are not equal (a situation that is allowed) then they are checked for an interior intersection, meaning that the interiors of the two components intersect (T********) or - only when curves are compared - that the boundary of one component intersects the interior of the other component (*T******* or ***T*****). If such a situation is detected, the intersection of the two components will be returned and testing will stop (meaning that the result will only provide information for one invalid intersection, not all intersections).
     *
     * @param geomNode1
     *            the node that represents the first geometry
     * @param geomNode2
     *            the node that represents the second geometry
     * @return The intersection of two components from the two geometries, where an invalid intersection was detected, or <code>null</code> if no such case exists.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry determineInteriorIntersectionOfCurveComponents(
            ANode geomNode1, ANode geomNode2) throws QueryException {

        try {

            /* Determine if the two geometries intersect at all. Only if they do, a more detailed computation is necessary. */

            Geometry g1 = null;
            Geometry g2 = null;

            com.vividsolutions.jts.geom.Geometry jtsg1 = null;
            com.vividsolutions.jts.geom.Geometry jtsg2 = null;

            // try to get JTS geometry for first geometry node from cache
            if (geomNode1 instanceof DBNode) {
                String geomId = getDBNodeID((DBNode) geomNode1);
                try {
                    jtsg1 = mgr.get(geomId);
                } catch (Exception e1) {
                    throw new QueryException(
                            "Exception occurred while getting JTS geometry from GeometryManager. Exception message is: "
                                    + e1.getMessage());
                }
            }
            if (jtsg1 == null) {
                g1 = geoutils.parseGeometry(geomNode1);
                jtsg1 = geoutils.toJTSGeometry(g1);
            }

            // now the same for the second geometry node
            if (geomNode2 instanceof DBNode) {
                String geomId = getDBNodeID((DBNode) geomNode2);
                try {
                    jtsg2 = mgr.get(geomId);
                } catch (Exception e1) {
                    throw new QueryException(
                            "Exception occurred while getting JTS geometry from GeometryManager. Exception message is: "
                                    + e1.getMessage());
                }
            }
            if (jtsg2 == null) {
                g2 = geoutils.parseGeometry(geomNode2);
                jtsg2 = geoutils.toJTSGeometry(g2);
            }

            /* If both geometries are points or multi-points, there cannot be a relevant intersection. */
            boolean g1IsPointGeom = jtsg1 instanceof com.vividsolutions.jts.geom.Point
                    || jtsg1 instanceof com.vividsolutions.jts.geom.MultiPoint;
            boolean g2IsPointGeom = jtsg2 instanceof com.vividsolutions.jts.geom.Point
                    || jtsg2 instanceof com.vividsolutions.jts.geom.MultiPoint;
            if (g1IsPointGeom && g2IsPointGeom) {
                return null;
            }

            /* Check if the two geometries intersect at all. If not, we are done. Otherwise, we need to check in more detail. */
            if (!jtsg1.intersects(jtsg2)) {
                return null;
            }

            /* deegree geometries may not exist yet, if JTS geometries have been retrieved from the geometry cache. Ensure that deegree geometries are available. */
            if (g1 == null) {
                g1 = geoutils.parseGeometry(geomNode1);
            }
            if (g2 == null) {
                g2 = geoutils.parseGeometry(geomNode2);
            }

            /* Determine JTS geometries for relevant geometry components */

            List<com.vividsolutions.jts.geom.Geometry> g1Components = new ArrayList<>();
            if (jtsg1 instanceof com.vividsolutions.jts.geom.Point) {
                g1Components.add(jtsg1);
            } else if (jtsg1 instanceof com.vividsolutions.jts.geom.MultiPoint) {
                com.vividsolutions.jts.geom.MultiPoint mp = (com.vividsolutions.jts.geom.MultiPoint) jtsg1;
                g1Components.addAll(
                        Arrays.asList(flattenAllGeometryCollections(mp)));
            } else {
                for (Curve c : geoutils.getCurveComponents(g1)) {
                    g1Components.add(geoutils.toJTSGeometry(c));
                }
            }

            List<com.vividsolutions.jts.geom.Geometry> g2Components = new ArrayList<>();
            if (jtsg2 instanceof com.vividsolutions.jts.geom.Point) {
                g2Components.add(jtsg2);
            } else if (jtsg2 instanceof com.vividsolutions.jts.geom.MultiPoint) {
                com.vividsolutions.jts.geom.MultiPoint mp = (com.vividsolutions.jts.geom.MultiPoint) jtsg2;
                g2Components.addAll(
                        Arrays.asList(flattenAllGeometryCollections(mp)));
            } else {
                for (Curve c : geoutils.getCurveComponents(g2)) {
                    g2Components.add(geoutils.toJTSGeometry(c));
                }
            }

            /* Switch order of geometry arrays, if the second geometry is point based. We want to create a spatial index only for curve components. */
            if (g2IsPointGeom) {
                List<com.vividsolutions.jts.geom.Geometry> tmp = g1Components;
                g1Components = g2Components;
                g2Components = tmp;
                tmp = null;
            }

            /* Create spatial index for curve components. */
            STRtree g2ComponentIndex = new STRtree();
            for (com.vividsolutions.jts.geom.Geometry g2CompGeom : g2Components) {
                g2ComponentIndex.insert(g2CompGeom.getEnvelopeInternal(),
                        g2CompGeom);
            }

            /* Now check for invalid interior intersections of components from the two geometries. */
            for (com.vividsolutions.jts.geom.Geometry g1CompGeom : g1Components) {

                // get g2 components from spatial index to compare
                @SuppressWarnings("rawtypes")
                List g2Results = g2ComponentIndex
                        .query(g1CompGeom.getEnvelopeInternal());

                for (Object o : g2Results) {
                    com.vividsolutions.jts.geom.Geometry g2CompGeom = (com.vividsolutions.jts.geom.Geometry) o;
                    if (g1CompGeom.intersects(g2CompGeom)) {
                        /* It is allowed that two curves are spatially equal. So only check for an interior intersection in case that the two geometry components are not spatially equal.
                         *
                         * The intersection matrix must be computed in any case (to determine that the two components are not equal). So we compute it once, and re-use it for tests on interior intersection. */
                        IntersectionMatrix im = g1CompGeom.relate(g2CompGeom);
                        if (!im.isEquals(g1CompGeom.getDimension(),
                                g2CompGeom.getDimension())
                                && (im.matches("T********") || (!(g1IsPointGeom
                                        || g2IsPointGeom)
                                        && (im.matches("*T*******")
                                                || im.matches("***T*****"))))) {
                            // invalid intersection detected
                            return g1CompGeom.intersection(g2CompGeom);
                        }
                    }
                }
            }

            /* No invalid intersection found. */
            return null;

        } catch (Exception e) {
            throw new QueryException(e);
        }
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
    public com.vividsolutions.jts.geom.Geometry getOrCacheGeometry(
            ANode geomNode) throws QueryException {

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
                    LOGGER.debug("Cache misses: " + mgr.getMissCount() + " of "
                            + mgr.getCount());
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
    public void prepareSpatialIndex(final ANode node, final ANode geometry)
            throws QueryException {

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
    public void prepareSpatialIndex(final String indexName, final ANode node,
            final ANode geometry) throws QueryException {

        if (node instanceof DBNode && geometry instanceof DBNode) {

            try {

                final com.vividsolutions.jts.geom.Geometry _geom = getOrCacheGeometry(
                        geometry);

                final Envelope env = _geom.getEnvelopeInternal();

                if (!env.isNull()) {

                    // cache the index entry
                    final DBNodeEntry nodeEntry = new DBNodeEntry(
                            (DBNode) node);

                    com.github.davidmoten.rtree.geometry.Geometry treeGeom;

                    if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {
                        treeGeom = Geometries.point(env.getMinX(),
                                env.getMinY());
                    } else {
                        treeGeom = Geometries.rectangle(env.getMinX(),
                                env.getMinY(), env.getMaxX(), env.getMaxY());
                    }
                    mgr.prepareSpatialIndex(indexName, nodeEntry, treeGeom);

                    // also cache the envelope
                    final DBNodeEntry geomNodeEntry = new DBNodeEntry(
                            (DBNode) geometry);
                    mgr.addEnvelope(geomNodeEntry, env);
                }

                if (debug && mgr.indexSize(indexName) % 5000 == 0) {
                    String debugIndexName = indexName != null ? indexName
                            : "default";
                    logMemUsage("GmlGeoX#index progress (for index '"
                            + debugIndexName + "'): "
                            + mgr.indexSize(indexName));
                }
            } catch (final Exception e) {
                if (e instanceof XMLParsingException) {
                    // otherwise the stacktrace "<< is empty >>" is included
                    throw new QueryException(prepareXMLParsingException(
                            (XMLParsingException) e));
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
     *             If the index has already been built.
     */
    @Requires(Permission.NONE)
    public void buildSpatialIndex(final String indexName)
            throws QueryException {

        mgr.buildIndexUsingBulkLoading(indexName);
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
    public String[] georefFromGeom(com.vividsolutions.jts.geom.Geometry geom)
            throws QueryException {

        if (geom == null || geom.isEmpty()) {
            return new String[]{};
        } else {
            Coordinate firstCoord = geom.getCoordinates()[0];
            return new String[]{"" + firstCoord.x, "" + firstCoord.y};
        }
    }

    /**
     * Retrieve x and y of the given coordinate, as strings without scientific notation.
     *
     * @param coord
     * @return an array with the x and y of the given coordinate, as strings without scientific notation.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String[] georefFromCoord(
            com.vividsolutions.jts.geom.Coordinate coord)
            throws QueryException {

        return new String[]{"" + coord.x, "" + coord.y};
    }

}
