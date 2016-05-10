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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessage;
import nl.vrom.roo.validator.core.dom4j.handlers.GeometryElementHandler;

import com.google.common.base.Joiner;
import com.vividsolutions.jts.geom.util.GeometryExtracter;
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion;

import org.basex.api.dom.BXElem;
import org.basex.api.dom.BXNode;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.value.Value;
import org.basex.query.value.node.ANode;
import org.basex.query.value.seq.Empty;
import org.deegree.geometry.Geometry;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * This module supports the validation of geometries as well as computing the
 * spatial relationship between geometries.
 * <p>
 * NOTE 1: the validation and spatial relationship methods only support specific
 * sets of geometry types - please see the documentation of the respective
 * methods for details on which geometry types are supported.
 *
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments
 *         <dot> de)
 *
 */
public class GmlGeoX extends QueryModule {

	public enum SpatialRelOp {
		CONTAINS, CROSSES, EQUALS, INTERSECTS, ISDISJOINT, ISWITHIN, OVERLAPS, TOUCHES
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(GmlGeoX.class);

	private static final Pattern INTERSECTIONPATTERN = Pattern
			.compile("[0-2\\*TF]{9}");

	protected GmlGeoXUtils geoutils = new GmlGeoXUtils();

	private final Set<String> gmlGeometries = new TreeSet<String>();

	public GmlGeoX() {

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
	}

	/**
	 * Calls the {@link #validate(Object, String)} method, with <code>null</code>
	 * as the bitmask, resulting in a validation with all tests enabled.
	 * <p>
	 * See the documentation of the {@link #validate(Object, String)} method for
	 * a description of the supported geometry types.
	 *
	 * @param o
	 * @return
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	public String validate(Object o) throws QueryException {
		return this.validate(o, null);
	}

	/**
	 * Validates the given (GML geometry) node.
	 * <p>
	 * By default validation is only performed for the following GML geometry
	 * elements: Point, Polygon, Surface, Curve, LinearRing, MultiPolygon,
	 * MultiGeometry, MultiSurface, MultiCurve, Ring, and LineString. The set of
	 * GML elements to validate can be modified via the following methods:
	 * {@link #registerGmlGeometry(String)},
	 * {@link #unregisterGmlGeometry(String)}, and
	 * {@link #unregisterAllGmlGeometries()}. These methods are also available
	 * for XQueries.
	 * <p>
	 * The validation tasks to perform can be specified via the given mask. The
	 * mask is a simple string, where the character '1' at the position of a
	 * specific test (assuming a 1-based index) specifies that the test shall be
	 * performed. If the mask does not contain a character at the position of a
	 * specific test (because the mask is empty or the length is smaller than
	 * the position), then the test will be executed.
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
	 * <td>This test validates the given geometry using the validation
	 * functionality of both deegree and JTS.</td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td>Polygon Patch Connectivity</td>
	 * <td>Checks that multiple polygon patches within a single surface are
	 * connected.</td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td>Repetition of Position in CurveSegments</td>
	 * <td>Checks that consecutive positions within a CurveSegment are not
	 * equal.</td>
	 * </tr>
	 * </table>
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>The mask '010' indicates that only the 'Polygon Patch Connectivity'
	 * test shall be performed.</li>
	 * <li>The mask '1' indicates that all tests shall be performed (because the
	 * first one is set to true/'1' and nothing is said for the other tests).
	 * </li>
	 * <li>The mask '0' indicates that all except the first test shall be
	 * performed.
	 * </ul>
	 *
	 * @param o
	 *            the GML geometry to validate
	 * @return a mask with the test results, encoded as characters - one at each
	 *         position (1-based index) of the available tests. 'V' indicates
	 *         that the test passed, i.e. that the geometry is valid according
	 *         to that test. 'F' indicates that the test failed. 'S' indicates
	 *         that the test was skipped. Example: the string 'SVF' shows that
	 *         the first test was skipped, while the second test passed and the
	 *         third failed.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	public String validate(Object o, String testMask) throws QueryException {

		try {

			// determine which tests to execute
			boolean isTestGeonovum, isTestPolygonPatchConnectivity,
					isTestRepetitionInCurveSegments;

			if (testMask == null) {

				isTestGeonovum = true;
				isTestPolygonPatchConnectivity = true;
				isTestRepetitionInCurveSegments = true;

			} else {

				isTestGeonovum = (testMask.length() >= 1
						&& testMask.charAt(0) == '1') ? true : false;
				isTestPolygonPatchConnectivity = (testMask.length() >= 2
						&& testMask.charAt(1) == '1') ? true : false;
				isTestRepetitionInCurveSegments = (testMask.length() >= 3
						&& testMask.charAt(2) == '1') ? true : false;
			}

			boolean isValidGeonovum = false;
			boolean polygonPatchesAreConnected = false;
			boolean noRepetitionInCurveSegment = false;

			BXNode elem;

			if (o instanceof ANode) {
				elem = ((ANode) o).toJava();
			} else if (o instanceof BXNode) {
				elem = (BXNode) o;
			} else {
				// unknown type encountered
				throw new IllegalArgumentException(
						"Object type '" + o.getClass().getName()
								+ "' is not supported for this method.");
			}

			// ================
			// Geonovum validation (deegree and JTS validation)

			if (isTestGeonovum) {

				ValidatorContext ctx = new ValidatorContext();

				GeometryElementHandler handler = new GeometryElementHandler(ctx,
						null);
				/*
				 * configure handler with GML geometries specified through this
				 * class
				 */
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
					List<ValidatorMessage> vmsgs = ctx.getMessages();
					for (ValidatorMessage msg : vmsgs) {
						LOGGER.error(msg.toString());
					}
				}
			}

			if (isTestPolygonPatchConnectivity
					|| isTestRepetitionInCurveSegments) {

				ValidatorContext ctx = new ValidatorContext();
				SecondaryGeometryElementValidationHandler handler = new SecondaryGeometryElementValidationHandler(
						isTestPolygonPatchConnectivity,
						isTestRepetitionInCurveSegments, ctx);

				/*
				 * configure handler with GML geometries specified through this
				 * class
				 */
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

			return sb.toString();

		} catch (Exception e) {
			throw new QueryException(e);
		}
	}

	/**
	 * Tests if the first geometry contains the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @return <code>true</code> if the first geometry contains the second one,
	 *         else <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean contains(Object arg1, Object arg2) throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.CONTAINS);
	}

	/**
	 * Tests if one geometry contains a list of geometries. Whether a match is
	 * required for all or just one of these is controlled via parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 *            element
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship for all geometries in arg2, else
	 *            <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean contains(Object arg1, Object arg2, boolean matchAll)
			throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.CONTAINS, matchAll);
	}

	/**
	 * Tests if the first geometry crosses the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @return <code>true</code> if the first geometry crosses the second one,
	 *         else <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean crosses(Object arg1, Object arg2) throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.CROSSES);
	}

	/**
	 * Tests if one geometry crosses a list of geometries. Whether a match is
	 * required for all or just one of these is controlled via parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 *            element
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship for all geometries in arg2, else
	 *            <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean crosses(Object arg1, Object arg2, boolean matchAll)
			throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.CROSSES, matchAll);
	}

	/**
	 * Tests if the first geometry equals the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @return <code>true</code> if the first geometry equals the second one,
	 *         else <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean equals(Object arg1, Object arg2) throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.EQUALS);
	}

	/**
	 * Tests if one geometry equals a list of geometries. Whether a match is
	 * required for all or just one of these is controlled via parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 *            element
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship for all geometries in arg2, else
	 *            <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean equals(Object arg1, Object arg2, boolean matchAll)
			throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.EQUALS, matchAll);
	}

	/**
	 * Tests if the first geometry intersects the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @return <code>true</code> if the first geometry intersects the second
	 *         one, else <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean intersects(Object arg1, Object arg2) throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.INTERSECTS);
	}

	public com.vividsolutions.jts.geom.Geometry parseGeometry(Object arg)
			throws QueryException {

		try {
			if (arg instanceof ANode) {

				return geoutils.toJTSGeometry((ANode) arg);

			} else if (arg instanceof BXElem) {

				return geoutils.toJTSGeometry((Element) arg);

			} else if (arg instanceof com.vividsolutions.jts.geom.Geometry) {

				return (com.vividsolutions.jts.geom.Geometry) arg;

			} else {
				throw new IllegalArgumentException(
						"First argument is neither a single node nor a JTS geometry.");
			}
		} catch (Exception e) {
			throw new QueryException(e);
		}
	}

	private boolean performSpatialRelationshipOperation(Object arg1,
			Object arg2, SpatialRelOp op) throws QueryException {

		try {

			com.vividsolutions.jts.geom.Geometry geom1, geom2;

			/*
			 * We require that no basex value with multiple items is provided,
			 * because the developer must explicitly state the desired match
			 * semantics for cases in which one or both arguments is a
			 * collection of items.
			 */
			geom1 = geoutils.singleObjectToJTSGeometry(arg1);
			geom2 = geoutils.singleObjectToJTSGeometry(arg2);

			return applySpatialRelationshipOperator(geom1, geom2, op);

		} catch (Exception e) {
			throw new QueryException(e);
		}
	}

	private boolean applySpatialRelationshipOperator(
			com.vividsolutions.jts.geom.Geometry geom1,
			com.vividsolutions.jts.geom.Geometry geom2, SpatialRelOp op) {

		switch (op) {
		case CONTAINS:
			return geom1.contains(geom2);
		case CROSSES:
			return geom1.crosses(geom2);
		case EQUALS:
			return geom1.equals(geom2);
		case INTERSECTS:
			return geom1.intersects(geom2);
		case ISDISJOINT:
			return geom1.disjoint(geom2);
		case ISWITHIN:
			return geom1.within(geom2);
		case OVERLAPS:
			return geom1.overlaps(geom2);
		case TOUCHES:
			return geom1.touches(geom2);
		default:
			throw new IllegalArgumentException(
					"Unknown spatial relationship operator: " + op.toString());
		}
	}

	private boolean performSpatialRelationshipOperation(Object arg1,
			Object arg2, SpatialRelOp op, boolean matchAll)
					throws QueryException {

		try {

			if (arg1 instanceof Empty || arg2 instanceof Empty) {

				return false;

			} else {

				com.vividsolutions.jts.geom.Geometry geom1, geom2;

				geom1 = geoutils.toJTSGeometry(arg1);
				geom2 = geoutils.toJTSGeometry(arg2);

				List<com.vividsolutions.jts.geom.Geometry> gc1, gc2;

				gc1 = geoutils.toFlattenedJTSGeometryCollection(geom1);
				gc2 = geoutils.toFlattenedJTSGeometryCollection(geom2);

				boolean allMatch = true;

				outer: for (com.vividsolutions.jts.geom.Geometry g1 : gc1) {
					for (com.vividsolutions.jts.geom.Geometry g2 : gc2) {

						if (matchAll) {

							if (applySpatialRelationshipOperator(g1, g2, op)) {
								/*
								 * check the next geometry pair to see if it
								 * also satisfies the spatial relationship
								 */
							} else {
								allMatch = false;
								break outer;
							}

						} else {

							if (applySpatialRelationshipOperator(g1, g2, op)) {
								return true;
							} else {
								/*
								 * check the next geometry pair to see if it
								 * satisfies the spatial relationship
								 */
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
	 * Tests if one geometry intersects a list of geometries. Whether a match is
	 * required for all or just one of these is controlled via parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 *            element
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship for all geometries in arg2, else
	 *            <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean intersects(Object arg1, Object arg2, boolean matchAll)
			throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.INTERSECTS, matchAll);
	}

	/**
	 * Tests if the first geometry is disjoint from the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @return <code>true</code> if the first geometry is disjoint from the
	 *         second one, else <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean isDisjoint(Object arg1, Object arg2) throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.ISDISJOINT);
	}

	/**
	 * Tests if one geometry is disjoint to a list of geometries. Whether a
	 * match is required for all or just one of these is controlled via
	 * parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 *            element
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship for all geometries in arg2, else
	 *            <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean isDisjoint(Object arg1, Object arg2, boolean matchAll)
			throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.ISDISJOINT, matchAll);
	}

	/**
	 * Tests if the first geometry is within the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @return <code>true</code> if the first geometry is within the second one,
	 *         else <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean isWithin(Object arg1, Object arg2) throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.ISWITHIN);
	}

	/**
	 * Tests if one geometry is within a list of geometries. Whether a match is
	 * required for all or just one of these is controlled via parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 *            element
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship for all geometries in arg2, else
	 *            <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean isWithin(Object arg1, Object arg2, boolean matchAll)
			throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.ISWITHIN, matchAll);
	}

	/**
	 * Tests if the first geometry overlaps the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @return <code>true</code> if the first geometry overlaps the second one,
	 *         else <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean overlaps(Object arg1, Object arg2) throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.OVERLAPS);
	}

	/**
	 * Tests if one geometry overlaps a list of geometries. Whether a match is
	 * required for all or just one of these is controlled via parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 *            element
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship for all geometries in arg2, else
	 *            <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean overlaps(Object arg1, Object arg2, boolean matchAll)
			throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.OVERLAPS, matchAll);
	}

	/**
	 * Tests if the first geometry touches the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @return <code>true</code> if the first geometry touches the second one,
	 *         else <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean touches(Object arg1, Object arg2) throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.TOUCHES);
	}

	/**
	 * Tests if one geometry touches a list of geometries. Whether a match is
	 * required for all or just one of these is controlled via parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 *            element
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship for all geometries in arg2, else
	 *            <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean touches(Object arg1, Object arg2, boolean matchAll)
			throws QueryException {

		return performSpatialRelationshipOperation(arg1, arg2,
				SpatialRelOp.TOUCHES, matchAll);
	}

	/**
	 * Adds the name of a GML geometry element to the set of elements for which
	 * validation is performed.
	 *
	 * @param nodeName
	 *            name (simple, i.e. without namespace (prefix)) of a GML
	 *            geometry element to validate.
	 */
	@Requires(Permission.NONE)
	public void registerGmlGeometry(String nodeName) {
		gmlGeometries.add(nodeName);
	}

	/**
	 * Removes the name of a GML geometry element from the set of elements for
	 * which validation is performed.
	 *
	 * @param nodeName
	 *            name (simple, i.e. without namespace (prefix)) of a GML
	 *            geometry element to remove from validation.
	 */
	@Requires(Permission.NONE)
	public void unregisterGmlGeometry(String nodeName) {
		gmlGeometries.remove(nodeName);
	}

	/**
	 * Removes all names of GML geometry elements that are currently registered
	 * for validation.
	 */
	@Requires(Permission.NONE)
	public void unregisterAllGmlGeometries() {
		gmlGeometries.clear();
	}

	/**
	 * @return the currently registered GML geometry element names (comma
	 *         separated)
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

	@Requires(Permission.NONE)
	@Deterministic
	public com.vividsolutions.jts.geom.Geometry union(Object arg1, Object arg2)
			throws QueryException {

		try {

			List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

			com.vividsolutions.jts.geom.Geometry geom1 = geoutils
					.toJTSGeometry(arg1);
			geoms.add(geom1);

			com.vividsolutions.jts.geom.Geometry geom2 = geoutils
					.toJTSGeometry(arg2);
			geoms.add(geom2);

			com.vividsolutions.jts.geom.GeometryCollection gc = geoutils
					.toJTSGeometryCollection(geoms);

			return gc.union();

		} catch (Exception e) {
			throw new QueryException(e);
		}
	}

	@Requires(Permission.NONE)
	@Deterministic
	public com.vividsolutions.jts.geom.Geometry union(Object arg)
			throws QueryException {

		try {

			List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<com.vividsolutions.jts.geom.Geometry>();

			com.vividsolutions.jts.geom.Geometry geom = geoutils
					.toJTSGeometry(arg);
			geoms.add(geom);

			com.vividsolutions.jts.geom.GeometryCollection gc = geoutils
					.toJTSGeometryCollection(geoms);

			return gc.union();

		} catch (Exception e) {
			throw new QueryException(e);
		}
	}

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
	 * Identifies the holes contained in the given geometry (can be a Polygon,
	 * MultiPolygon, or any other JTS geometry) and returns them as a JTS
	 * geometry. If holes were found a union is built, to ensure that the result
	 * is a valid JTS Polygon or JTS MultiPolygon. If no holes were found an
	 * empty JTS GeometryCollection is returned.
	 *
	 * @param geom
	 *            potentially existing holes will be extracted from this
	 *            geometry
	 * @return A geometry with the holes contained in the given geometry. Can be
	 *         empty but not <code>null</code>;
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public com.vividsolutions.jts.geom.Geometry holes(
			com.vividsolutions.jts.geom.Geometry geom) {

		if (isEmpty(geom)) {

			return geoutils.emptyJTSGeometry();

		} else {

			List<com.vividsolutions.jts.geom.Polygon> extractedPolygons = new ArrayList<com.vividsolutions.jts.geom.Polygon>();

			GeometryExtracter.extract(geom,
					com.vividsolutions.jts.geom.Polygon.class,
					extractedPolygons);

			if (extractedPolygons.isEmpty()) {

				return geoutils.emptyJTSGeometry();

			} else {

				// get holes as polygons
				List<com.vividsolutions.jts.geom.Polygon> holes = new ArrayList<com.vividsolutions.jts.geom.Polygon>();

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

				if (holes.isEmpty()) {
					return geoutils.emptyJTSGeometry();
				} else {
					// create union of holes and return it
					return CascadedPolygonUnion.union(holes);
				}
			}
		}
	}

	@Requires(Permission.NONE)
	public boolean isValid(Object o) throws QueryException {

		if (o == null || o instanceof Empty) {

			return false;

		} else if (o instanceof BXElem || o instanceof ANode) {

			String validationResult = validate(o);

			if (validationResult.toLowerCase().indexOf('f') > -1) {
				return false;
			} else {
				return true;
			}

		} else if (o instanceof Value) {

			Value v = (Value) o;

			if (v.size() > 1) {
				throw new IllegalArgumentException(
						"Single value expected where multiple were provided.");
			}

		} else if (o instanceof Object[]) {
			throw new IllegalArgumentException(
					"Single object expected where multiple were provided.");
		}

		// unknown type encountered
		throw new IllegalArgumentException(
				"Object type '" + o.getClass().getName()
						+ "' is not supported for this method.");
	}

	/**
	 * Tests if the first geometry relates to the second geometry as defined by
	 * the given intersection pattern.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents the second geometry, encoded as a GML geometry
	 *            element
	 * @param intersectionPattern
	 *            the pattern against which to check the intersection matrix for
	 *            the two geometries (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
	 * @return <code>true</code> if the DE-9IM intersection matrix for the two
	 *         geometries matches the <code>intersectionPattern</code>, else
	 *         <code>false</code>.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean relate(Object arg1, Object arg2, String intersectionPattern)
			throws QueryException {

		checkIntersectionPattern(intersectionPattern);

		try {

			com.vividsolutions.jts.geom.Geometry geom1, geom2;

			/*
			 * We require that no basex value with multiple items is provided,
			 * because the developer must explicitly state the desired match
			 * semantics for cases in which one or both arguments is a
			 * collection of items.
			 */
			geom1 = geoutils.singleObjectToJTSGeometry(arg1);
			geom2 = geoutils.singleObjectToJTSGeometry(arg2);

			return geom1.relate(geom2, intersectionPattern);

		} catch (Exception e) {
			throw new QueryException(e);
		}
	}

	/**
	 * Tests if one geometry relates to a list of geometries as defined by the
	 * given intersection pattern. Whether a match is required for all or just
	 * one of these is controlled via parameter.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry, encoded as a GML geometry
	 *            element
	 * @param arg2
	 *            represents a list of geometries, encoded as a GML geometry
	 * @param intersectionPattern
	 *            the pattern against which to check the intersection matrix for
	 *            the geometries (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
	 * @param matchAll
	 *            <code>true</code> if arg1 must fulfill the spatial
	 *            relationship defined by the <code>intersectionPattern</code>
	 *            for all geometries in arg2, else <code>false</code>
	 * @return <code>true</code> if the conditions are met, else
	 *         <code>false</code>. <code>false</code> will also be returned if
	 *         arg2 is empty.
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public boolean relate(Object arg1, Object arg2, String intersectionPattern,
			boolean matchAll) throws QueryException {

		checkIntersectionPattern(intersectionPattern);

		try {

			if (arg1 instanceof Empty || arg2 instanceof Empty) {

				return false;

			} else {

				com.vividsolutions.jts.geom.Geometry geom1, geom2;

				geom1 = geoutils.toJTSGeometry(arg1);
				geom2 = geoutils.toJTSGeometry(arg2);

				List<com.vividsolutions.jts.geom.Geometry> gc1, gc2;

				gc1 = geoutils.toFlattenedJTSGeometryCollection(geom1);
				gc2 = geoutils.toFlattenedJTSGeometryCollection(geom2);

				boolean allMatch = true;

				outer: for (com.vividsolutions.jts.geom.Geometry g1 : gc1) {
					for (com.vividsolutions.jts.geom.Geometry g2 : gc2) {

						if (matchAll) {

							if (g1.relate(g2, intersectionPattern)) {
								/*
								 * check the next geometry pair to see if it
								 * also satisfies the spatial relationship
								 */
							} else {
								allMatch = false;
								break outer;
							}

						} else {

							if (g1.relate(g2, intersectionPattern)) {
								return true;
							} else {
								/*
								 * check the next geometry pair to see if it
								 * satisfies the spatial relationship
								 */
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

	private void checkIntersectionPattern(String intersectionPattern)
			throws QueryException {

		if (intersectionPattern == null) {

			throw new QueryException("intersectionPattern is null.");

		} else {

			Matcher m = INTERSECTIONPATTERN.matcher(intersectionPattern.trim());

			if (!m.matches()) {
				throw new QueryException(
						"intersectionPattern does not match the regular expression, which is: [0-2\\\\*TF]{9}");
			}
		}

	}

	/**
	 * Computes the intersection between the first and the second geometry.
	 * <p>
	 * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported
	 * and unsupported geometry types.
	 *
	 * @param arg1
	 *            represents the first geometry
	 * @param arg2
	 *            represents the second geometry
	 * @return the point-set common to the two geometries
	 * @throws QueryException
	 */
	@Requires(Permission.NONE)
	@Deterministic
	public com.vividsolutions.jts.geom.Geometry intersection(Object arg1,
			Object arg2) throws QueryException {

		try {

			if (arg1 instanceof Empty || arg2 instanceof Empty) {

				return geoutils.emptyJTSGeometry();

			} else {

				com.vividsolutions.jts.geom.Geometry geom1, geom2;

				geom1 = geoutils.toJTSGeometry(arg1);
				geom2 = geoutils.toJTSGeometry(arg2);

				return geom1.intersection(geom2);
			}

		} catch (Exception e) {
			throw new QueryException(e);
		}

	}

}
