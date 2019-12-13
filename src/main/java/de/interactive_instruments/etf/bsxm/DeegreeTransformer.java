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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.io.FileUtils;
import org.basex.query.value.Value;
import org.basex.query.value.node.ANode;
import org.deegree.commons.xml.stax.XMLStreamReaderWrapper;
import org.deegree.cs.CRSCodeType;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.composite.CompositeCurve;
import org.deegree.geometry.composite.CompositeGeometry;
import org.deegree.geometry.multi.MultiGeometry;
import org.deegree.geometry.multi.MultiPoint;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.LineString;
import org.deegree.geometry.primitive.OrientableCurve;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.Ring;
import org.deegree.geometry.primitive.Solid;
import org.deegree.geometry.primitive.Surface;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.patches.SurfacePatch;
import org.deegree.geometry.primitive.segments.CurveSegment;
import org.deegree.geometry.standard.curvesegments.DefaultArc;
import org.deegree.geometry.standard.curvesegments.DefaultArcByBulge;
import org.deegree.geometry.standard.curvesegments.DefaultArcString;
import org.deegree.geometry.standard.curvesegments.DefaultArcStringByBulge;
import org.deegree.geometry.standard.curvesegments.DefaultBSpline;
import org.deegree.geometry.standard.curvesegments.DefaultBezier;
import org.deegree.geometry.standard.curvesegments.DefaultCubicSpline;
import org.deegree.geometry.standard.curvesegments.DefaultGeodesic;
import org.deegree.geometry.standard.curvesegments.DefaultGeodesicString;
import org.deegree.geometry.standard.curvesegments.DefaultLineStringSegment;
import org.deegree.geometry.standard.points.PointsPoints;
import org.deegree.geometry.standard.points.PointsSubsequence;
import org.deegree.geometry.standard.primitive.DefaultCurve;
import org.deegree.gml.GMLInputFactory;
import org.deegree.gml.GMLStreamReader;
import org.deegree.gml.GMLVersion;
import org.jetbrains.annotations.NotNull;

import de.interactive_instruments.IFile;
import de.interactive_instruments.IoUtils;
import de.interactive_instruments.etf.bsxm.geometry.ArcRevWrapper;
import de.interactive_instruments.etf.bsxm.geometry.ArcStringRevWrapper;
import de.interactive_instruments.etf.bsxm.geometry.IIGeometryFactory;
import de.interactive_instruments.etf.bsxm.geometry.LineStringRevWrapper;
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
    private final SrsLookup srsLookup;

    DeegreeTransformer(final IIGeometryFactory deegreeGeomFac, final BxNamespaceHolder namespaceHolder,
            final SrsLookup srsLookup) {
        this.geometryFactory = deegreeGeomFac;
        this.namespaceHolder = namespaceHolder;
        this.srsLookup = srsLookup;

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
    public Geometry parseGeometry(ANode aNode) throws GmlGeoXException {

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

        final ICRS crs = srsLookup.getSrsForGeometryNode(aNode);
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

    /**
     * Retrieves all basic curve components from the given geometry. Composite geometries - including curves - will be broken up into their parts. Point based geometries will be ignored.
     *
     * @param geom
     * @return A list with the curve components of the given geometry. Can be empty but not <code>null</code>.
     * @throws Exception
     */
    public Collection<Curve> getCurveComponents(final @NotNull Geometry geom) throws GmlGeoXException {
        if (geom instanceof DefaultCurve) {
            return Collections.singleton((DefaultCurve) geom);
        } else if (geom instanceof CompositeCurve) {
            final CompositeCurve cc = (CompositeCurve) geom;
            final List<Curve> result = new ArrayList<>(cc.size());
            for (int i = 0; i < cc.size(); i++) {
                result.addAll(getCurveComponents(cc.get(i)));
            }
            return result;
        } else if (geom instanceof LineString) {
            return Collections.singleton((LineString) geom);
        } else if (geom instanceof OrientableCurve) {
            /* 2015-12-14 JE: special treatment is necessary because OrientableCurve.getJTSGeometry() returns null (with code from deegree 3.4-pre22-SNAPSHOT). */
            final OrientableCurve oc = (OrientableCurve) geom;
            final Curve baseCurve = oc.getBaseCurve();
            return getCurveComponents(baseCurve);
        } else if (geom instanceof Ring) {
            final Ring r = (Ring) geom;
            final List<Curve> result = new ArrayList<>(r.getMembers().size());
            for (Curve c : r.getMembers()) {
                result.addAll(getCurveComponents(c));
            }
            return result;
        } else if (geom instanceof Surface) {
            // covers CompositeSurface, OrientableSurface, Polygon, ...
            final Surface s = (Surface) geom;
            final List<? extends SurfacePatch> patches = s.getPatches();
            final List<Curve> result = new ArrayList<>();
            for (SurfacePatch sp : patches) {
                final List<? extends Ring> boundaryRings;
                if (sp instanceof PolygonPatch) {
                    boundaryRings = ((PolygonPatch) sp).getBoundaryRings();
                } else {
                    throw new UnsupportedGeometryTypeException(
                            "Surface contains a surface patch that is not a polygon patch, a rectangle, or a triangle.");
                }
                for (Ring r : boundaryRings) {
                    result.addAll(getCurveComponents(r));
                }
            }
            return result;
        } else if (geom instanceof Point || geom instanceof MultiPoint) {
            // ignore
            return Collections.emptyList();
        } else if (geom instanceof Solid) {
            final Solid s = (Solid) geom;
            final List<Curve> result = new ArrayList<>();
            if (s.getExteriorSurface() != null) {
                result.addAll(getCurveComponents(s.getExteriorSurface()));
            }
            for (Surface surface : s.getInteriorSurfaces()) {
                result.addAll(getCurveComponents(surface));
            }
            return result;
        } else if (geom instanceof MultiGeometry) {
            final MultiGeometry mg = (MultiGeometry) geom;
            final List<Curve> result = new ArrayList<>(mg.size());
            for (int i = 0; i < mg.size(); i++) {
                result.addAll(getCurveComponents((Geometry) mg.get(i)));
            }
            return result;
        } else if (geom instanceof CompositeGeometry) {
            final CompositeGeometry cg = (CompositeGeometry) geom;
            final List<Curve> result = new ArrayList<>(cg.size());
            for (int i = 0; i < cg.size(); i++) {
                result.addAll(getCurveComponents((Geometry) cg.get(i)));
            }
            return result;
        } else {
            throw new GmlGeoXException(
                    "Determination of curve components for deegree geometry type '"
                            + geom.getClass().getName()
                            + "' is not supported.");
        }
    }

    /**
     * Retrieves all basic curve components from the given geometry node. Composite geometries - including curves - will be broken up into their parts. Point based geometries will be ignored.
     *
     * @param geomNode
     *            GML geometry node
     * @return A list with the curve components of the given geometry. Can be empty but not <code>null</code>.
     * @throws Exception
     */
    public Collection<Curve> getCurveComponents(final @NotNull ANode geomNode) throws GmlGeoXException {

        final Geometry geom_deegree = parseGeometry(geomNode);
        return getCurveComponents(geom_deegree);
    }

    public List<Curve> getCurveComponents(final @NotNull Value geomNodes) throws GmlGeoXException {

        List<Curve> result = new ArrayList<>();

        @SuppressWarnings("rawtypes")
        final Collection otherGeomNodesObjectList = GmlGeoX.toObjectCollection(geomNodes);
        for (Object o : otherGeomNodesObjectList) {
            if (!(o instanceof ANode)) {
                throw new GmlGeoXException(
                        "Calling getCurveComponents(Value) with a parameter that is not an ANode is illegal.");
            } else {
                result.addAll(getCurveComponents((ANode) o));
            }
        }

        return result;
    }

    /**
     * Getting the control points from a deegree Curve via Curve.getControlPoints() is, according to the javadoc of that method, only safe for linearly interpolated curves. Therefore, we use a different way to compute the control points.
     *
     * @param curve
     * @throws IllegalArgumentException
     *             If the curve contains an unsupported type of curve segment
     */
    public Points getCurveControlPoints(Curve curve) throws IllegalArgumentException {

        final List<CurveSegment> curveSegments = curve.getCurveSegments();

        final List<Points> pointsList = new ArrayList<Points>(curveSegments.size());
        boolean first = true;

        for (CurveSegment cs : curve.getCurveSegments()) {

            Points p;

            if (cs instanceof DefaultLineStringSegment) {
                p = ((DefaultLineStringSegment) cs).getControlPoints();
            } else if (cs instanceof LineStringRevWrapper) {
                p = ((LineStringRevWrapper) cs).getControlPoints();
            } else if (cs instanceof DefaultArcString) {
                p = ((DefaultArcString) cs).getControlPoints();
            } else if (cs instanceof ArcStringRevWrapper) {
                p = ((ArcStringRevWrapper) cs).getControlPoints();
            } else if (cs instanceof DefaultArc) {
                p = ((DefaultArc) cs).getControlPoints();
            } else if (cs instanceof ArcRevWrapper) {
                p = ((ArcRevWrapper) cs).getControlPoints();
            } else if (cs instanceof DefaultArcStringByBulge) {
                p = ((DefaultArcStringByBulge) cs).getControlPoints();
            } else if (cs instanceof DefaultArcByBulge) {
                p = ((DefaultArcByBulge) cs).getControlPoints();
            } else if (cs instanceof DefaultBSpline) {
                p = ((DefaultBSpline) cs).getControlPoints();
            } else if (cs instanceof DefaultBezier) {
                p = ((DefaultBezier) cs).getControlPoints();
            } else if (cs instanceof DefaultCubicSpline) {
                p = ((DefaultCubicSpline) cs).getControlPoints();
            } else if (cs instanceof DefaultGeodesicString) {
                p = ((DefaultGeodesicString) cs).getControlPoints();
            } else if (cs instanceof DefaultGeodesic) {
                p = ((DefaultGeodesic) cs).getControlPoints();
            } else {
                throw new IllegalArgumentException("Computing control points from a curve segment of type "
                        + cs.getSegmentType().name() + " is not supported.");
            }

            if (first) {
                pointsList.add(p);
                first = false;
            } else {
                /* starting with the second segment, skip the first point (as it *must* be identical to last point of the last segment) */
                pointsList.add(new PointsSubsequence(p, 1));
            }
        }

        return new PointsPoints(pointsList);
    }
}
