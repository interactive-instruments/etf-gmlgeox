/**
 * Copyright 2010-2016 interactive instruments GmbH
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion;

import org.basex.api.dom.BXElem;
import org.basex.api.dom.BXNode;
import org.basex.query.QueryException;
import org.basex.query.QueryIOException;
import org.basex.query.value.Value;
import org.basex.query.value.item.Item;
import org.basex.query.value.item.Jav;
import org.basex.query.value.node.ANode;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.composite.CompositeCurve;
import org.deegree.geometry.composite.CompositeGeometry;
import org.deegree.geometry.composite.CompositeSolid;
import org.deegree.geometry.composite.CompositeSurface;
import org.deegree.geometry.multi.*;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.OrientableCurve;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.Ring;
import org.deegree.geometry.primitive.Surface;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.patches.SurfacePatch;
import org.deegree.geometry.standard.AbstractDefaultGeometry;
import org.deegree.gml.GMLInputFactory;
import org.deegree.gml.GMLStreamReader;
import org.deegree.gml.GMLVersion;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 *
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments
 *         <dot> de)
 *
 */
public class GmlGeoXUtils {

	protected XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

	/**
	 * Used to built JTS geometries.
	 */
	protected com.vividsolutions.jts.geom.GeometryFactory jtsFactory = new com.vividsolutions.jts.geom.GeometryFactory();

	/**
	 * Creates a JTS Polygon from the given deegree PolygonPatch.
	 *
	 * @param patch
	 * @return
	 */
	public Polygon toJTSPolygon(PolygonPatch patch) {

		Ring exteriorRing = patch.getExteriorRing();
		List<Ring> interiorRings = patch.getInteriorRings();

		com.vividsolutions.jts.geom.LinearRing shell = (com.vividsolutions.jts.geom.LinearRing) ((AbstractDefaultGeometry) exteriorRing)
				.getJTSGeometry();
		com.vividsolutions.jts.geom.LinearRing[] holes = null;

		if (interiorRings != null) {

			holes = new com.vividsolutions.jts.geom.LinearRing[interiorRings
					.size()];

			int i = 0;
			for (Ring ring : interiorRings) {
				holes[i++] = (com.vividsolutions.jts.geom.LinearRing) ((AbstractDefaultGeometry) ring)
						.getJTSGeometry();
			}
		}

		return jtsFactory.createPolygon(shell, holes);
	}

	/**
	 * Creates a JTS Polygon from the given JTS LineString.
	 *
	 * @param exterior
	 * @return
	 */
	public Polygon toJTSPolygon(
			com.vividsolutions.jts.geom.LineString exterior) {

		com.vividsolutions.jts.geom.LinearRing exteriorRing = jtsFactory
				.createLinearRing(exterior.getCoordinates());

		return jtsFactory.createPolygon(exteriorRing, null);
	}

	/**
	 * Creates a JTS GeometryCollection from the list of JTS geometry objects.
	 *
	 * @param gList
	 * @return a JTS GeometryCollection (empty if the given list is
	 *         <code>null</code> or empty)
	 */
	public GeometryCollection toJTSGeometryCollection(
			List<com.vividsolutions.jts.geom.Geometry> gList) {

		if (gList == null || gList.isEmpty()) {

			return jtsFactory.createGeometryCollection(null);

		} else {

			GeometryCollection gc = jtsFactory.createGeometryCollection(
					gList.toArray(new com.vividsolutions.jts.geom.Geometry[gList
							.size()]));

			return gc;
		}
	}

	/**
	 * Creates a JTS MultiPolygon from the list of JTS polygon objects.
	 *
	 * @param gList list of JTS Polygons
	 * @return a JTS MultiPolygon (or an empty GeometryCollection if the given list is
	 *         <code>null</code> or empty)
	 */
	public GeometryCollection toJTSMultiPolygon(
			List<com.vividsolutions.jts.geom.Polygon> gList) {

		if (gList == null || gList.isEmpty()) {

			return jtsFactory.createGeometryCollection(null);

		} else {

			GeometryCollection gc = jtsFactory.createMultiPolygon(
					gList.toArray(new com.vividsolutions.jts.geom.Polygon[gList.size()]));

			return gc;
		}
	}

	/**
	 * Creates a JTS MultiLineString from the list of JTS polygon objects.
	 *
	 * @param gList list of JTS LineStrings
	 * @return a JTS MultiLineString (or an empty GeometryCollection if the given list is
	 *         <code>null</code> or empty)
	 */
	public GeometryCollection toJTSMultiLineString(
			List<com.vividsolutions.jts.geom.LineString> gList) {

		if (gList == null || gList.isEmpty()) {

			return jtsFactory.createGeometryCollection(null);

		} else {

			GeometryCollection gc = jtsFactory.createMultiLineString(
					gList.toArray(new com.vividsolutions.jts.geom.LineString[gList.size()]));

			return gc;
		}
	}

	/**
	 * Creates a JTS MultiPoint from the list of JTS polygon objects.
	 *
	 * @param gList list of JTS Points
	 * @return a JTS MultiPoint  (or an empty GeometryCollection if the given list is
	 *         <code>null</code> or empty)
	 */
	public GeometryCollection toJTSMultiPoint(
			List<com.vividsolutions.jts.geom.Point> gList) {

		if (gList == null || gList.isEmpty()) {

			return jtsFactory.createGeometryCollection(null);

		} else {

			GeometryCollection gc = jtsFactory.createMultiPoint(
					gList.toArray(new com.vividsolutions.jts.geom.Point[gList.size()]));

			return gc;
		}
	}

	/**
	 * Computes a JTS geometry from the given deegree geometry.
	 *
	 * Supported geometry types:
	 * <ul>
	 * <li>GM_Point</li>
	 * <li>Curve types:</li>
	 * <ul>
	 * <li>GM_Curve</li>
	 * <li>GM_Ring</li>
	 * <li>GM_LinearRing</li>
	 * <li>GM_LineString</li>
	 * <li>GM_OrientedCurve (orientation is ignored when computing the JTS
	 * geometry)</li>
	 * </ul>
	 * <li>Curve segment types (linearization will often be used):</li>
	 * <ul>
	 * <li>GM_Arc (will be linearized)</li>
	 * <li>GM_Circle (will be linearized)</li>
	 * <li>GM_LineString</li>
	 * <li>GM_CubicSpline (will be linearized)</li>
	 * <li>GM_ArcString (will be linearized)</li>
	 * <li>GM_GeodesicString (apparently no linearization - with code from
	 * deegree 3.4-pre22-SNAPSHOT)</li>
	 * </ul>
	 * <li>Surface types:</li>
	 * <ul>
	 * <li>GM_Surface</li>
	 * <li>GM_PolyhedralSurface</li>
	 * <li>GM_OrientableSurface (orientation is ignored when computing the JTS
	 * geometry)</li>
	 * </ul>
	 * <li>Surface patch types (also if a surface has more than one patch):</li>
	 * <ul>
	 * <li>GM_Polygon</li>
	 * </ul>
	 * <li>Composite types:</li>
	 * <ul>
	 * <li>GM_Composite (implemented as deegree CompositeGeometry)</li>
	 * <li>GM_CompositePoint</li>
	 * <li>GM_CompositeCurve</li>
	 * <li>GM_CompositeSurface</li>
	 * </ul>
	 * <li>Multi geometry types:</li>
	 * <ul>
	 * <li>GM_Aggregate (implemented as deegree MultiGeometry)</li>
	 * <li>GM_MultiPoint</li>
	 * <li>GM_MultiCurve</li>
	 * <li>GM_MultiSurface</li>
	 * </ul>
	 * </ul>
	 * <p>
	 * Geometry types that are NOT supported:
	 * <ul>
	 * <li>GM_Solid</li>
	 * <li>Curve segment types:</li>
	 * <ul>
	 * <li>GM_ArcByBulge</li>
	 * <li>ArcByCenterPoint</li>
	 * <li>GM_ArcStringByBulge</li>
	 * <li>GM_Bezier</li>
	 * <li>GM_BSplineCurve</li>
	 * <li>CircleByCenterPoint</li>
	 * <li>GM_Clothoid</li>
	 * <li>GM_Geodesic</li>
	 * <li>GM_OffsetCurve</li>
	 * <li>GM_Conic</li>
	 * </ul>
	 * <li>Surface types:</li>
	 * <ul>
	 * <li>GM_TriangulatedSurface</li>
	 * <li>GM_Tin</li>
	 * <li>GM_ParametricCurveSurface</li>
	 * <li>GM_GriddedSurface</li>
	 * <li>GM_BilinearGrid</li>
	 * <li>GM_BicubicGrid</li>
	 * <li>GM_Cone</li>
	 * <li>GM_Cylinder</li>
	 * <li>GM_Sphere</li>
	 * <li>GM_BSplineSurface</li>
	 * </ul>
	 * <li>Composite types:</li>
	 * <ul>
	 * <li>GM_CompositeSolid</li>
	 * </ul>
	 * <li>Multi geometry types:</li>
	 * <ul>
	 * <li>GM_MultiSolid</li>
	 * </ul>
	 * </ul>
	 *
	 * @param geom
	 * @return
	 * @throws Exception
	 */
	public com.vividsolutions.jts.geom.Geometry toJTSGeometry(Geometry geom)
			throws Exception {

		if (geom instanceof Surface) {

			/*
			 * Deegree does not support spatial operations for surfaces with
			 * more than one patch - or rather: it ignores all patches except
			 * the first one. So we need to detect and handle this case
			 * ourselves.
			 *
			 * In fact, it is DefaultSurface that does not support multiple
			 * patches. So we could check that the geometry is an instance of
			 * DefaultSurface. However, it is not planned to create another
			 * Geometry-Implementation for deegree. Thus we treat each Surface
			 * as having the issue of not supporting JTS geometry creation if it
			 * has multiple patches.
			 *
			 * Because we compute the JTS geometry of a surface directly from
			 * its patch(es), we don't need a special treatment for the case of
			 * an OrientableSurface. Much like for OrientableCurve, the deegree
			 * framework returns null when OrientableSurface.getJTSGeometry() is
			 * called (with code from deegree 3.4-pre22-SNAPSHOT).
			 */

			Surface s = (Surface) geom;

			List<? extends SurfacePatch> patches = s.getPatches();

			if (patches.size() > 1) {

				/*
				 * compute union - only supportd if all patches are polygon
				 * patches
				 */
				List<com.vividsolutions.jts.geom.Polygon> polygons = new ArrayList<com.vividsolutions.jts.geom.Polygon>();

				for (SurfacePatch sp : patches) {

					if (sp instanceof PolygonPatch) {

						com.vividsolutions.jts.geom.Polygon p = toJTSPolygon(
								(PolygonPatch) sp);

						polygons.add(p);

					} else {
						throw new UnsupportedGeometryTypeException(
								"Surface contains multiple surface patches. At least one patch is not a polygon patch. Cannot create a JTS geometry.");
					}
				}

				// create union and return it
				return CascadedPolygonUnion.union(polygons);

			} else {

				SurfacePatch sp = patches.get(0);

				if (sp instanceof PolygonPatch) {

					return toJTSPolygon((PolygonPatch) sp);

				} else {
					throw new UnsupportedGeometryTypeException(
							"Surface contains a single surface patch that is not a polygon patch. Cannot create a JTS geometry.");
				}
			}

		} else if (geom instanceof OrientableCurve) {

			/*
			 * 2015-12-14 JE: special treatment is necessary because
			 * OrientableCurve.getJTSGeometry() returns null (with code from
			 * deegree 3.4-pre22-SNAPSHOT).
			 */

			try {
				OrientableCurve oc = (OrientableCurve) geom;
				Curve baseCurve = oc.getBaseCurve();

				return ((AbstractDefaultGeometry) baseCurve).getJTSGeometry();

			} catch (IllegalArgumentException e) {
				throw new UnsupportedGeometryTypeException(
						"Curve (or one of its segments) is of a type for which computation of the JTS geometry is not supported.");

			}
		} else if (geom instanceof Curve) {

			try {
				return ((AbstractDefaultGeometry) geom).getJTSGeometry();

			} catch (IllegalArgumentException e) {
				throw new UnsupportedGeometryTypeException(
						"Curve (or one of its segments) is of a type for which computation of the JTS geometry is not supported.");
			}
		} else if (geom instanceof Point) {

			try {
				return ((AbstractDefaultGeometry) geom).getJTSGeometry();

			} catch (UnsupportedOperationException e) {
				throw new UnsupportedGeometryTypeException(
						"Computation of the JTS geometry for a point is only supported if the number of coordinates is either 2 or 3. A different number of coordinates was encountered.");
			}

		} else if (geom instanceof MultiSolid) {
			throw new UnsupportedGeometryTypeException(
					"Computation of the JTS geometry for a MultiSolid is not supported.");

		} else if (geom instanceof MultiSurface) {

			@SuppressWarnings("rawtypes")
			MultiSurface mg = (MultiSurface) geom;

			List<com.vividsolutions.jts.geom.Polygon> gList = new ArrayList<com.vividsolutions.jts.geom.Polygon>();

			for (Object o : mg) {
				Geometry geo = (Geometry) o;
				com.vividsolutions.jts.geom.Geometry g = toJTSGeometry(geo);
				if (g instanceof com.vividsolutions.jts.geom.Polygon)
					gList.add((com.vividsolutions.jts.geom.Polygon) g);
				else
					throw new UnsupportedGeometryTypeException("A geometry was found in a MultiSurface that could not be converted to a JTS Polygon.");
			}

			GeometryCollection gc = toJTSMultiPolygon(gList);
			return gc;

		} else if (geom instanceof MultiCurve) {

			@SuppressWarnings("rawtypes")
			MultiCurve mg = (MultiCurve) geom;

			List<com.vividsolutions.jts.geom.LineString> gList = new ArrayList<com.vividsolutions.jts.geom.LineString>();

			for (Object o : mg) {
				Geometry geo = (Geometry) o;
				com.vividsolutions.jts.geom.Geometry g = toJTSGeometry(geo);
				if (g instanceof com.vividsolutions.jts.geom.LineString)
					gList.add((com.vividsolutions.jts.geom.LineString) g);
				else
					throw new UnsupportedGeometryTypeException("A geometry was found in a MultiCurve that could not be converted to a JTS LineString.");
			}

			GeometryCollection gc = toJTSMultiLineString(gList);
			return gc;

		} else if (geom instanceof MultiPoint) {

			@SuppressWarnings("rawtypes")
			MultiPoint mg = (MultiPoint) geom;

			List<com.vividsolutions.jts.geom.Point> gList = new ArrayList<com.vividsolutions.jts.geom.Point>();

			for (Object o : mg) {
				Geometry geo = (Geometry) o;
				com.vividsolutions.jts.geom.Geometry g = toJTSGeometry(geo);
				if (g instanceof com.vividsolutions.jts.geom.Point)
					gList.add((com.vividsolutions.jts.geom.Point) g);
				else
					throw new UnsupportedGeometryTypeException("A geometry was found in a MultiPoint that could not be converted to a JTS Point.");
			}

			GeometryCollection gc = toJTSMultiPoint(gList);
			return gc;

		} else if (geom instanceof MultiGeometry) {

			@SuppressWarnings("rawtypes")
			MultiGeometry mg = (MultiGeometry) geom;

			List<com.vividsolutions.jts.geom.Geometry> gList = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

			for (Object o : mg) {
				Geometry geo = (Geometry) o;
				com.vividsolutions.jts.geom.Geometry g = toJTSGeometry(geo);
				gList.add(g);
			}

			GeometryCollection gc = toJTSGeometryCollection(gList);
			return gc;

		} else if (geom instanceof CompositeSolid) {
			throw new UnsupportedGeometryTypeException(
					"Computation of the JTS geometry for a CompositeSolid is not supported.");

		} else if (geom instanceof CompositeGeometry) {

			@SuppressWarnings("rawtypes")
			CompositeGeometry cg = (CompositeGeometry) geom;

			List<com.vividsolutions.jts.geom.Geometry> gList = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

			for (Object o : cg) {
				Geometry geo = (Geometry) o;
				com.vividsolutions.jts.geom.Geometry g = toJTSGeometry(geo);
				gList.add(g);
			}

			GeometryCollection gc = toJTSGeometryCollection(gList);
			return gc;

		} else if (geom instanceof CompositeCurve) {

			return ((AbstractDefaultGeometry) geom).getJTSGeometry();

		} else if (geom instanceof CompositeSurface) {

			return ((AbstractDefaultGeometry) geom).getJTSGeometry();

		} else {

			throw new QueryException(
					"Computation of JTS geometry for deegree geometry type '"
							+ geom.getClass().getName()
							+ "' is not supported.");
		}
	}

	/**
	 * Computes a JTS geometry from the given node (which must represent a GML
	 * geometry).
	 * <p>
	 * See {{@link #toJTSGeometry(Geometry)} for a list of supported and
	 * unsupported geometry types.
	 *
	 * @param node
	 * @return
	 * @throws Exception
	 */
	public com.vividsolutions.jts.geom.Geometry toJTSGeometry(ANode node)
			throws Exception {

		if (node == null) {

			throw new IllegalArgumentException(
					"Cannot compute JTS geometry because given node is null.");

		} else {

			Geometry geom = parseGeometry(node);

			return toJTSGeometry(geom);
		}
	}

	/**
	 * Computes a JTS geometry from the given element (which must represent a
	 * GML geometry).
	 * <p>
	 * See {{@link #toJTSGeometry(Geometry)} for a list of supported and
	 * unsupported geometry types.
	 *
	 * @param e
	 * @return
	 * @throws Exception
	 */
	public com.vividsolutions.jts.geom.Geometry toJTSGeometry(Element e)
			throws Exception {

		if (e == null) {

			throw new IllegalArgumentException(
					"Cannot compute JTS geometry because given element is null.");

		} else {

			Geometry geom = parseGeometry(e);

			return toJTSGeometry(geom);
		}
	}

	public Geometry parseGeometry(ANode aNode) throws Exception {
		BXNode node = aNode.toJava();
		return parseGeometry(node);
	}

	public com.vividsolutions.jts.geom.Geometry toJTSGeometry(Object o)
			throws Exception {

		if (o == null) {

			return emptyJTSGeometry();

		} else if (o instanceof Value) {

			Value v = (Value) o;

			if (v.size() > 1) {

				List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

				for (Item i : v) {
					com.vividsolutions.jts.geom.Geometry geom = toJTSGeometry(
							i);
					geoms.add(geom);
				}

				return toJTSGeometryCollection(geoms);

			} else {

				return singleObjectToJTSGeometry(o);
			}

		} else if (o instanceof Object[]) {

			List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

			for (Object os : (Object[]) o) {
				com.vividsolutions.jts.geom.Geometry geom = toJTSGeometry(os);
				geoms.add(geom);
			}

			return toJTSGeometryCollection(geoms);

		} else {

			return singleObjectToJTSGeometry(o);
		}
	}

	public com.vividsolutions.jts.geom.Geometry singleObjectToJTSGeometry(
			Object o) throws Exception {

		if (o instanceof BXElem) {

			return toJTSGeometry((Element) o);

		} else if (o instanceof Value) {

			Value v = (Value) o;

			if (v.size() > 1) {
				throw new IllegalArgumentException(
						"Single value expected where multiple were provided.");
			} else {
				if (v instanceof ANode) {

					return toJTSGeometry((ANode) v);

				} else if (v instanceof Jav) {

					return (com.vividsolutions.jts.geom.Geometry) ((Jav) v)
							.toJava();
				} else {
					throw new IllegalArgumentException(
							"Conversion of BaseX Value type "
									+ v.getClass().getCanonicalName()
									+ " to geometry is not supported.");
				}
			}
		} else if (o instanceof com.vividsolutions.jts.geom.Geometry) {

			return (com.vividsolutions.jts.geom.Geometry) o;

		} else {
			throw new IllegalArgumentException(
					"Argument cannot be converted to a single JTS geometry.");
		}

	}

	/**
	 * Reads a geometry from the given DOM node.
	 *
	 * @param node
	 *            represents a GML geometry element
	 * @return the geometry represented by the node
	 * @throws Exception
	 */
	public Geometry parseGeometry(Node node) throws Exception {

		String namespaceURI = node.getNamespaceURI();

		if (namespaceURI == null || (!isGML32Namespace(namespaceURI)
				&& !isGML31Namespace(namespaceURI))) {

			throw new Exception("Cannot identify GML version from namespace '"
					+ (namespaceURI == null ? "<null>" : namespaceURI) + "'.");
		}

		final InputStream byteArrayInputStream = nodeToInputStream(node);

		final XMLStreamReader xmlStream = xmlInputFactory
				.createXMLStreamReader(byteArrayInputStream);

		GMLVersion gmlVersion = null;
		if (isGML32Namespace(namespaceURI)) {
			gmlVersion = GMLVersion.GML_32;
		} else if (isGML31Namespace(namespaceURI)) {
			gmlVersion = GMLVersion.GML_31;
		} else {
			// cannot happen because we checked before
		}

		GMLStreamReader gmlStream = GMLInputFactory
				.createGMLStreamReader(gmlVersion, xmlStream);

		Geometry result = gmlStream.readGeometry();

		return result;
	}

	/**
	 * Return text representation of a node as InputStream
	 *
	 * @param node
	 * @return
	 * @throws TransformerException
	 */
	public InputStream nodeToInputStream(final Node node)
			throws TransformerException {

		if (node instanceof BXNode) {
			// BXNode does not support the getPrefix(), which is required by saxon.
			try {
				return new ByteArrayInputStream(((BXNode) node).getNode().serialize().toArray());
			} catch (QueryIOException e) {
				throw new TransformerException(e);
			}
		}

		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		final Result outputTarget = new StreamResult(outputStream);
		final Transformer t = TransformerFactory.newInstance().newTransformer();
		t.transform(new DOMSource(node), outputTarget);
		return new ByteArrayInputStream(outputStream.toByteArray());
	}

	public boolean isGML32Namespace(String namespaceURI) {
		return GMLVersion.GML_32.getNamespace().equals(namespaceURI);
	}

	public boolean isGML31Namespace(String namespaceURI) {
		return GMLVersion.GML_31.getNamespace().equals(namespaceURI);
	}

	public com.vividsolutions.jts.geom.Geometry emptyJTSGeometry() {
		return jtsFactory.createGeometryCollection(null);
	}

	/**
	 * Adds a geometry to a collection. If the geometry is a GeometryCollection
	 * (but not a MultiPoint, -LineString, or -Polygon) its members are added
	 * (recursively scanning for GeometryCollections). Spatial relationship
	 * operators cannot be performed for a JTS GeometryCollection, but for
	 * (Multi)Point, (Multi)LineString, and (Multi)Polygon.
	 *
	 * @param geom
	 * @return
	 */
	public List<com.vividsolutions.jts.geom.Geometry> toFlattenedJTSGeometryCollection(
			com.vividsolutions.jts.geom.Geometry geom) {

		final List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

		if (isGeometryCollectionButNotASubtype(geom)) {
			toFlattenedJTSGeometryCollection((GeometryCollection) geom, geoms);
		} else {
			geoms.add(geom);
		}

		return geoms;
	}

	protected void toFlattenedJTSGeometryCollection(
			com.vividsolutions.jts.geom.GeometryCollection geomColl,
			List<com.vividsolutions.jts.geom.Geometry> geoms) {

		for (int i = 0; i < geomColl.getNumGeometries(); i++) {

			com.vividsolutions.jts.geom.Geometry geom = geomColl
					.getGeometryN(i);

			if (isGeometryCollectionButNotASubtype(geom)) {

				toFlattenedJTSGeometryCollection((GeometryCollection) geom,
						geoms);

			} else {

				geoms.add(geom);
			}
		}
	}

	protected boolean isGeometryCollectionButNotASubtype(
			com.vividsolutions.jts.geom.Geometry geom) {

		if (geom instanceof GeometryCollection
				&& !(geom instanceof com.vividsolutions.jts.geom.MultiPoint
						|| geom instanceof com.vividsolutions.jts.geom.MultiLineString
						|| geom instanceof com.vividsolutions.jts.geom.MultiPolygon)) {
			return true;
		} else {
			return false;
		}
	}
}
