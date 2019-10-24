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

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.cmd.CreateDB;
import org.basex.core.cmd.DropDB;
import org.basex.core.cmd.XQuery;
import org.junit.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

import de.interactive_instruments.IFile;

/**
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments <dot> de)
 *
 */
public class BasicXQueryTest {

    private static Context context = new Context();
    private final XmlUnitDetailFormatter formatter = new XmlUnitDetailFormatter();

    public static final String queryDir = "src/test/resources/queries/";
    public static final String xmlDir = "src/test/resources/xml/";

    // public static final String resultDir = "build/test-results/test/res/";
    // for local tests during development
    public static final String resultDir = "testResults/";

    public static final String referenceDir = "src/test/resources/reference/";

    @Test
    public void test_validation() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/geometryRelationship/GeometryRelationshipTest.xml")
                .execute(context);
        xmlTest("test_geometry_validation.xq");
    }

    @Test
    public void test_basic_tests() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/geometryRelationship/GeometryRelationshipTest.xml")
                .execute(context);
        xmlTest("test_basic_tests.xq");
    }

    @Test
    public void test_isClosed() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/GeometryIsClosedTest.xml")
                .execute(context);
        xmlTest("test_geometry_isClosed.xq");
    }

    @Test
    public void test_3d() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_geometry_3d.xml").execute(context);
        xmlTest("test_geometry_3d.xq");
    }

    @Test
    public void test_3d_indexed() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/3DCoodinates.xml").execute(context);
        xmlTest("test_geometry_3d_indexed.xq");
    }

    @Test
    public void test_SRS_configByGmlGeoX() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_geometry_SRS_configByGmlGeoX.xml")
                .execute(context);
        xmlTest("test_geometry_SRS_configByGmlGeoX.xq");
    }

    @Test
    public void test_union() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_geometry_union.xml").execute(context);
        xmlTest("test_geometry_union.xq");
    }

    @Test
    public void test_arc_interpolation() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_arc_interpolation.xml").execute(context);
        xmlTest("test_arc_interpolation.xq");
    }

    @Test
    public void test_arc_interpolation_orientableCurve() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_arc_interpolation_orientableCurve.xml")
                .execute(context);
        xmlTest("test_arc_interpolation_orientableCurve.xq");
    }

    @Test
    public void test_arc_interpolation_compositeSurface() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_arc_interpolation_compositeSurface.xml")
                .execute(context);
        xmlTest("test_arc_interpolation_compositeSurface.xq");
    }

    @Test
    public void test_arc_interpolation_compositeCurve() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_arc_interpolation_compositeCurve.xml")
                .execute(context);
        xmlTest("test_arc_interpolation_compositeCurve.xq");
    }

    @Test
    public void test_arc_interpolation_self_intersection() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_arc_interpolation_self_intersection.xml")
                .execute(context);
        xmlTest("test_arc_interpolation_self_intersection.xq");
    }

    @Test
    public void test_envelope() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_envelope.xml").execute(context);
        xmlTest("test_envelope.xq");
    }

    @Test
    public void test_checkSecondControlPointInMiddleThirdOfArc() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_checkSecondControlPointInMiddleThirdOfArc.xml")
                .execute(context);
        xmlTest("test_checkSecondControlPointInMiddleThirdOfArc.xq");
    }

    @Test
    public void test_checkMinimumSeparationOfCircleControlPoints() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_checkMinimumSeparationOfCircleControlPoints.xml")
                .execute(context);
        xmlTest("test_checkMinimumSeparationOfCircleControlPoints.xq");
    }

    @Test
    public void test_determineInteriorIntersectionOfCurveComponents() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_determineInteriorIntersectionOfCurveComponents.xml")
                .execute(context);
        xmlTest("test_determineInteriorIntersectionOfCurveComponents.xq");
    }

    @Test
    public void test_curveUnmatchedByIdenticalCurvesMin() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000",
                "src/test/resources/xml/test_curveUnmatchedByIdenticalCurvesMin.xml")
                        .execute(context);
        xmlTest("test_curveUnmatchedByIdenticalCurvesMin.xq");
    }

    @Test
    public void test_curveUnmatchedByIdenticalCurvesMax() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000",
                "src/test/resources/xml/test_curveUnmatchedByIdenticalCurvesMax.xml")
                        .execute(context);
        xmlTest("test_curveUnmatchedByIdenticalCurvesMax.xq");
    }

    @Test
    public void test_curveEndpoints() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000",
                "src/test/resources/xml/test_curveEndpoints.xml")
                        .execute(context);
        xmlTest("test_curveEndpoints.xq");
    }

    @Test
    public void test_moduleStorage() throws BaseXException {

        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/test_moduleStorage.xml")
                .execute(context);

        xmlTest("test_moduleStorage_store.xq");

        Map<String, String> externalVariables = new HashMap<>();
        externalVariables.put("restoreModules", "true");
        xmlTest("test_moduleStorage_restore.xq", externalVariables);
    }

    @Test
    public void test_graphConnectivity() throws BaseXException {

        new DropDB("GmlGeoXUnitTestDB-000").execute(context);
        new CreateDB("GmlGeoXUnitTestDB-000", "src/test/resources/xml/graphx/test_graphConnectivity.xml")
                .execute(context);

        xmlTest("graphx/test_graphConnectivity.xq");
    }

    private void xmlTest(String xquery) {
        xmlTest(xquery, null, null);
    }

    private void xmlTest(String xquery, Map<String, String> externalVariables) {
        xmlTest(xquery, null, externalVariables);
    }

    private void xmlTest(String xquery, String doc, Map<String, String> externalVariables) {

        try {
            String filename = FilenameUtils.getBaseName(xquery) + ".xml";

            String query = new String(Files.readAllBytes(Paths.get(queryDir + xquery)));

            XQuery xq = new XQuery(query);

            if (doc != null) {
                /* The XQuery must declare external variable 'docPath' that expects the path to the input XML.
                 *
                 * Example: declare variable $docPath external := '...'; */
                xq.bind("docPath", xmlDir + doc);
            }

            if (externalVariables != null) {
                for (Entry<String, String> entry : externalVariables.entrySet()) {
                    xq.bind(entry.getKey(), entry.getValue());
                }
            }

            String queryresult = xq.execute(context);

            String result = resultDir + filename;

            FileUtils.writeStringToFile(new File(result), queryresult, "utf-8");

            String reference = referenceDir + filename;

            similar(result, reference);

        } catch (Exception e) {
            e.printStackTrace(System.err);
            fail("Exception occurred while executing test with xquery '" + xquery + "': " + e.getMessage());
        }
    }

    private void similar(final String xmlFileName, final String referenceXmlFileName) {
        final IFile controlXmlFile = new IFile(referenceXmlFileName);
        final IFile testXmlFile = new IFile(xmlFileName);
        try {
            controlXmlFile.expectFileIsReadable();
            testXmlFile.expectFileIsReadable();
        } catch (final IOException e) {
            fail("Could not read " + referenceXmlFileName);
        }
        try {
            final Diff diff = DiffBuilder.compare(controlXmlFile.readContent("UTF-8").toString())
                    .withTest(Input.fromString(testXmlFile.readContent("UTF-8").toString()))
                    .checkForSimilar().checkForIdentical()
                    .ignoreComments()
                    .ignoreWhitespace()
                    .normalizeWhitespace()
                    .ignoreElementContentWhitespace()
                    .build();

            if (diff.hasDifferences()) {
                System.err.println("Test file: " + testXmlFile.getAbsolutePath());
                System.err.println("Expected file: " + controlXmlFile.getAbsolutePath());
                final Difference difference = diff.getDifferences().iterator().next();
                assertEquals(formatter.getControlDetailDescription(difference.getComparison()),
                        formatter.getTestDetailDescription(difference.getComparison()));
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            fail("Could not compare " + xmlFileName + " and " + referenceXmlFileName);
        }
    }
}
