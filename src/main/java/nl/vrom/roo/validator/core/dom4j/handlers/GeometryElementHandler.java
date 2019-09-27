package nl.vrom.roo.validator.core.dom4j.handlers;

import java.io.ByteArrayInputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.deegree.commons.xml.XMLParsingException;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.composite.CompositeGeometry;
import org.deegree.geometry.composite.CompositeSolid;
import org.deegree.geometry.multi.MultiGeometry;
import org.deegree.geometry.multi.MultiSolid;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.Ring;
import org.deegree.geometry.primitive.Solid;
import org.deegree.geometry.primitive.Surface;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.patches.SurfacePatch;
import org.deegree.geometry.primitive.segments.Arc;
import org.deegree.geometry.primitive.segments.ArcString;
import org.deegree.geometry.primitive.segments.CubicSpline;
import org.deegree.geometry.primitive.segments.CurveSegment;
import org.deegree.geometry.primitive.segments.GeodesicString;
import org.deegree.geometry.primitive.segments.LineStringSegment;
import org.deegree.geometry.standard.points.PointsList;
import org.deegree.gml.GMLInputFactory;
import org.deegree.gml.GMLStreamReader;
import org.deegree.gml.GMLVersion;
import org.dom4j.Element;
import org.dom4j.ElementHandler;
import org.dom4j.ElementPath;
import org.dom4j.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.operation.IsSimpleOp;
import com.vividsolutions.jts.operation.valid.IsValidOp;
import com.vividsolutions.jts.operation.valid.TopologyValidationError;

import de.interactive_instruments.etf.bsxm.GmlGeoXUtils;
import de.interactive_instruments.etf.bsxm.IIGeometryFactory;
import de.interactive_instruments.etf.bsxm.IIGeometryValidator;
import de.interactive_instruments.etf.bsxm.UnsupportedGeometryTypeException;
import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.dom4j.Dom4JHelper;
import nl.vrom.roo.validator.core.errorlocation.IdErrorLocation;

/**
 * GeometryElementHandler handles <code>Element</code> objects in the
 * gmlNamespace.
 *
 * @author rbrinkman
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments <dot>
 *         de)
 * 
 */
public class GeometryElementHandler implements ElementHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryElementHandler.class);

    private static final NumberFormat COORD_FORMAT = new DecimalFormat("0.000#######",
	    new DecimalFormatSymbols(Locale.ENGLISH));

    private static final String NODE_NAME_FEATURE_MEMBER = "featureMember";

    private final ValidatorContext validatorContext;

    private final List<String> gmlGeometries = new ArrayList<String>();

    private final Map<String, Integer> currentGmlGeometryCounters = new HashMap<String, Integer>();

    private String currentFeatureMember;

    private boolean isTestGeneral;
    private boolean isTestRepetitionInCurveSegments;
    private boolean isTestPolygonPatchConnectivity;
    private boolean isTestIsSimple;

    private boolean generallyValid = true;
    private boolean noRepetitionInCurveSegments = true;
    private boolean polygonPatchesAreConnected = true;
    private boolean isSimple = true;

    private Element currentElement;

    private boolean isGMLVersionReported;

    private String defaultSrsName;

    protected GmlGeoXUtils geoutils;
    protected IIGeometryFactory geometryFactory;

    /**
     * Instantiates a new geometry element handler.
     * 
     * @param isTestGeneral
     * @param isTestPolygonPatchConnectivity
     * @param isTestRepetitionInCurveSegments
     * @param isTestIsSimple
     * @param validatorContext                the validatorContext used during
     *                                        handling
     * @param parameters                      the parameters for handling
     * @param srsName                         the name of the default SRS to use
     *                                        when validating a geometry (especially
     *                                        important in case of geometries with
     *                                        3D coordinates, and srsName is not
     *                                        defined on the geometry element
     *                                        itself)
     * @param geoutils                        GmlGeoX utilities
     * @param geomFac                         the factory to be used when
     *                                        constructing geometry objects
     */
    public GeometryElementHandler(boolean isTestGeneral, boolean isTestPolygonPatchConnectivity,
	    boolean isTestRepetitionInCurveSegments, boolean isTestIsSimple, ValidatorContext validatorContext,
	    Map<String, Object> parameters, String srsName, GmlGeoXUtils geoutils, IIGeometryFactory geomFac) {

	this.isTestGeneral = isTestGeneral;
	this.isTestPolygonPatchConnectivity = isTestPolygonPatchConnectivity;
	this.isTestRepetitionInCurveSegments = isTestRepetitionInCurveSegments;
	this.isTestIsSimple = isTestIsSimple;

	this.validatorContext = validatorContext;

	this.defaultSrsName = srsName;

	this.geoutils = geoutils;

	this.geometryFactory = geomFac;

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

    @Override
    /**
     * {@inheritDoc}
     * <p>
     * When this is called on a new feature member, the geometry counters are reset.
     * </p>
     */
    public void onStart(ElementPath elementPath) {

	String currentPath = elementPath.getPath();

	if (NODE_NAME_FEATURE_MEMBER.equals(Dom4JHelper.getNodeFromPath(Dom4JHelper.getParentPath(currentPath)))) {
	    currentFeatureMember = Dom4JHelper.getNodeFromPath(currentPath);
	    resetGmlGeometryCounters();
	}
    }

    @Override
    public void onEnd(ElementPath elementPath) {
	String nodeName = Dom4JHelper.getNodeFromPath(elementPath.getPath());
	if (gmlGeometries.contains(nodeName)) {

	    // Check if this element is a main geometry
	    if (isMainGeometry(elementPath)) {

		raiseGmlGeometryCounter(nodeName);

		currentElement = elementPath.getCurrent();

		try {
		    validate(validatorContext, currentElement);
		} catch (XMLParsingException e) {
		    LOGGER.error("Unexpected error detected while validating geometry", e);
		} catch (UnknownCRSException e) {
		    LOGGER.error("Unexpected error detected while validating geometry", e);
		}
	    } else {
		LOGGER.trace("Element {} is part of another geometry", nodeName);
	    }
	}
    }

    private void validate(ValidatorContext validatorContext, Element element)
	    throws XMLParsingException, UnknownCRSException {

	Namespace namespace = element.getNamespace();
	String namespaceURI = namespace == null ? null : namespace.getURI();

	if (namespace == null
		|| (!geoutils.isGML32Namespace(namespaceURI) && !geoutils.isGML31Namespace(namespaceURI))) {

	    LOGGER.error("Unable to determine GML version. Namespace= {}", namespaceURI);

	    String errMessage = ValidatorMessageBundle.getMessage("validator.core.validation.geometry.no-gml",
		    namespaceURI);

	    validatorContext.addError(errMessage);
	    return;
	}

	if (!isGMLVersionReported) {
	    if (geoutils.isGML32Namespace(namespaceURI)) {
		validatorContext.addNotice(
			ValidatorMessageBundle.getMessage("validator.core.validation.geometry.gmlversion", "3.2"));
	    } else if (geoutils.isGML31Namespace(namespaceURI)) {
		validatorContext.addNotice(
			ValidatorMessageBundle.getMessage("validator.core.validation.geometry.gmlversion", "3.1"));
	    }
	    isGMLVersionReported = true;
	}

	try {
	    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(element.asXML().getBytes());

	    XMLStreamReader xmlStream = XMLInputFactory.newInstance().createXMLStreamReader(byteArrayInputStream);

	    GMLVersion gmlVersion = null;
	    if (geoutils.isGML32Namespace(namespaceURI)) {
		// GML 3.2
		gmlVersion = GMLVersion.GML_32;
	    } else if (geoutils.isGML31Namespace(namespaceURI)) {
		gmlVersion = GMLVersion.GML_31;
	    } else {
		throw new Exception("Cannot determine GML version");
	    }

	    GMLStreamReader gmlStream = GMLInputFactory.createGMLStreamReader(gmlVersion, xmlStream);
	    gmlStream.setGeometryFactory(geometryFactory);

	    ICRS defaultCRS = null;
	    if (defaultSrsName != null) {
		defaultCRS = CRSManager.getCRSRef(defaultSrsName);
	    }

	    gmlStream.setDefaultCRS(defaultCRS);

	    org.deegree.geometry.Geometry geom = gmlStream.readGeometry();
	    com.vividsolutions.jts.geom.Geometry jtsGeom = null;

	    if (isTestGeneral || isTestIsSimple || isTestPolygonPatchConnectivity) {
		
		try {
		jtsGeom = geoutils.toJTSGeometry(geom);
		}catch (Exception e) {
		    String currentGmlId = Dom4JHelper.findGmlId(element);

		    String message = getLocationDescription(element, currentGmlId) + ": " + e.getMessage();

		    validatorContext.addError(message, new IdErrorLocation(currentGmlId));
		    
		    if(isTestGeneral) generallyValid = false;
		    if(isTestIsSimple) isSimple = false;
		    if(isTestPolygonPatchConnectivity) polygonPatchesAreConnected = false;
		}
	    }

	    // ================
	    // Test: general validity
	    if (isTestGeneral && generallyValid) {
		boolean isValid = checkGeneralValidity(geom, jtsGeom, gmlVersion);
		if (!isValid) {
		    generallyValid = false;
		}
	    }

	    // ================
	    // Test: polygon patches of a surface are connected
	    if (isTestPolygonPatchConnectivity && polygonPatchesAreConnected) {
		boolean isValid = checkConnectivityOfPolygonPatches(geom, jtsGeom);
		if (!isValid) {
		    polygonPatchesAreConnected = false;
		}
	    }

	    // ================
	    // Test: point repetition in curve segment
	    if (isTestRepetitionInCurveSegments) {
		boolean isValid = checkNoRepetitionInCurveSegment(geom);
		if (!isValid) {
		    noRepetitionInCurveSegments = false;
		}
	    }

	    // ================
	    // Test: isSimple
	    if (isTestIsSimple && isSimple) {

		IsSimpleOp op = new IsSimpleOp(jtsGeom);
		boolean isValid = op.isSimple();
		if (!isValid) {
		    Coordinate nonSimpleLocation = op.getNonSimpleLocation();
		    if (nonSimpleLocation != null) {
			validatorContext.addError("Geometry is not simple. Self-intersection detected at "
				+ nonSimpleLocation.toString() + ".");
		    }

		    isSimple = false;
		}
	    }

	} catch (XMLStreamException | XMLParsingException e) {

	    String currentGmlId = Dom4JHelper.findGmlId(element);

	    String message = getLocationDescription(element, currentGmlId) + ": " + e.getMessage();

	    validatorContext.addError(message, new IdErrorLocation(currentGmlId));

	    // exceptions while parsing the deegree geometry fall under the general validity
	    // test
	    generallyValid = false;

	    /*
	     * 2019-09-18 JE: The exception is reported as an error. I don't see a need to
	     * also log the exception, especially the stack trace.
	     */
//            LOGGER.error(e.getMessage(), e);

	} catch (FactoryConfigurationError e) {

	    LOGGER.error(e.getMessage(), e);
	    validatorContext.addError(
		    ValidatorMessageBundle.getMessage("validator.core.validation.geometry.unknown-exception"));
	    
	} catch (Exception e) {
	    String currentGmlId = Dom4JHelper.findGmlId(element);

	    String message = getLocationDescription(element, currentGmlId) + ": " + e.getMessage();

	    validatorContext.addError(message, new IdErrorLocation(currentGmlId));
	    
	    LOGGER.error(e.getMessage(), e);
	}

	/*
	 * 2015-12-08 Johannes Echterhoff: it is not clear to me that detaching is
	 * relevant.
	 */
	// finally {
	// // Only permitted if this is a main geometry....
	// if (isMainGeometry(element)) {
	// element.detach();
	// }
	// }
    }

    protected boolean checkGeneralValidity(org.deegree.geometry.Geometry geom, Geometry jtsGeom,
	    GMLVersion gmlVersion) {

	boolean result = true;

	/*
	 * Perform JTS based validation. It will determine if the (potentially
	 * linearized) JTS geometry is valid. The result can be used as precondition for
	 * further validation by the geometry validator (especially orientation checks,
	 * which are only reliable for a valid geometry).
	 */
	boolean jtsValidationSucceeded = handleDeegree3GMLJTSValidation(jtsGeom);
	result = result & jtsValidationSucceeded;

	GMLValidationEventHandler eventHandler = new GMLValidationEventHandler(validatorContext, currentElement,
		gmlVersion == GMLVersion.GML_31);

	IIGeometryValidator validator = new IIGeometryValidator(eventHandler, geometryFactory);

	// Deegree3 based validation
	boolean deegreeValidationSucceeded = validator.validateGeometry(geom, jtsValidationSucceeded);
	result = result & deegreeValidationSucceeded;

	return result;
    }

    /**
     * To be used when JTS needs to be used for GML validation
     * 
     * @param geom
     * @return <code>true</code> if the geometry is valid, else <code>false</code>
     */
    private boolean handleDeegree3GMLJTSValidation(Geometry jtsGeometry) {

	try {
	    return handleGMLJtsValidation(jtsGeometry, currentElement);
	} catch (IllegalArgumentException e) {
	    String currentGmlId = Dom4JHelper.findGmlId(currentElement);

	    LOGGER.debug("{} {} within element with gml:id {} is not supported: {}",
		    new Object[] { currentElement.getName(), currentGmlGeometryCounters.get(currentElement.getName()),
			    currentGmlId, e.getMessage() });

	    String currentOnderdeelName = currentElement.getQualifiedName();

	    String errMessage = ValidatorMessageBundle.getMessage("validator.core.validation.geometry.unsupported",
		    currentGmlId, currentOnderdeelName, e.getMessage());

	    this.validatorContext.addError(errMessage, new IdErrorLocation(currentGmlId));
	    return false;
	}
    }

    /**
     * @param jtsGeometry
     * @param element
     * @return <code>true</code> if the geometry is valid, else <code>false</code>
     */
    private boolean handleGMLJtsValidation(com.vividsolutions.jts.geom.Geometry jtsGeometry, Element element) {

	IsValidOp ivo = new IsValidOp(jtsGeometry);
	// Optimization: ivo.isValid() is the same as topError==null. Otherwise
	// the validation is performed twice in case of geometry problems.
	TopologyValidationError topError = ivo.getValidationError();

	String currentGmlId = Dom4JHelper.findGmlId(element);
	if (topError == null) {

	    LOGGER.trace("{} {} within element with gml:id {} is valid", new Object[] { element.getName(),
		    currentGmlGeometryCounters.get(element.getName()), currentGmlId });
	    return true;
	} else {
	    String coordinatesText = generateCoordinatesText(jtsGeometry.getCoordinates(), topError.getCoordinate());

	    LOGGER.trace("{} {} within element with gml:id {} is invalid", new Object[] { element.getName(),
		    currentGmlGeometryCounters.get(element.getName()), currentGmlId });

	    String message = getLocationDescription(element, currentGmlId) + ": " + topError.getMessage() + ". "
		    + coordinatesText;

	    validatorContext.addError(message, new IdErrorLocation(currentGmlId));
	    return false;
	}
    }

    private String getLocationDescription(Element element, String gmlId) { // NOPMD
									   // -
									   // Method
									   // is
									   // not
									   // empty
	return ValidatorMessageBundle.getMessage("validator.core.validation.geometry.coordinates-position",
		new Object[] { element.getName(), currentGmlGeometryCounters.get(element.getName()),
			currentFeatureMember, gmlId });
    }

    private String generateCoordinatesText(Coordinate[] coordinates, Coordinate coordProblem) {
	String coordinatesText = null;

	if (coordinates.length > 0) {
	    Coordinate coordBegin = coordinates[0];
	    Coordinate coordEnd = coordinates[coordinates.length - 1];

	    if (coordProblem == null) {
		coordinatesText = ValidatorMessageBundle.getMessage(
			"validator.core.validation.geometry.coordinates-text-simple",
			new Object[] { formatValue(coordBegin.x), formatValue(coordBegin.y), formatValue(coordEnd.x),
				formatValue(coordEnd.y) });
	    } else {
		coordinatesText = ValidatorMessageBundle
			.getMessage("validator.core.validation.geometry.coordinates-text",
				new Object[] { formatValue(coordBegin.x), formatValue(coordBegin.y),
					formatValue(coordEnd.x), formatValue(coordEnd.y), formatValue(coordProblem.x),
					formatValue(coordProblem.y) });
	    }
	}
	return coordinatesText;
    }

    private String formatValue(double value) { // NOPMD - Method is not empty
	return COORD_FORMAT.format(value);
    }

    /**
     * Check if this is a main geometry by making sure that the parent tag is in one
     * of the imro namespaces
     *
     * @param elementPath
     * @return true is this is a main geometry
     */
    private boolean isMainGeometry(ElementPath elementPath) {
	// if(elementPath.getCurrent().getParent()==null) {
	// return false;
	// }
	// String namespaceURI =
	// elementPath.getCurrent().getParent().getNamespaceURI();
	// return (!isGML32Namespace(namespaceURI) &&
	// !isGML31Namespace(namespaceURI));

	/*
	 * 2015-12-08 Johannes Echterhoff: the previous logic is not suitable for us, in
	 * case that a single geometry (node) is validated by a (x)query. Because then
	 * the geometry has no parent, which would cause this method to evaluate to
	 * false - which is not what we want.
	 */

	if (elementPath.getCurrent().getParent() == null) {

	    // the geometry is the only XML that is validated
	    return true;

	} else {

	    // if the parent namespace is not a GML one, we have a main geometry
	    String namespaceURI = elementPath.getCurrent().getParent().getNamespaceURI();
	    return (!geoutils.isGML32Namespace(namespaceURI) && !geoutils.isGML31Namespace(namespaceURI));
	}
    }

    public void registerGmlGeometry(String nodeName) {
	gmlGeometries.add(nodeName);
	currentGmlGeometryCounters.put(nodeName, 0);
    }

    public void unregisterGmlGeometry(String nodeName) {
	gmlGeometries.remove(nodeName);
	currentGmlGeometryCounters.remove(nodeName);
    }

    public void unregisterAllGmlGeometries() {
	gmlGeometries.clear();
	currentGmlGeometryCounters.clear();
    }

    private void raiseGmlGeometryCounter(String nodeName) {
	Integer newCounter = currentGmlGeometryCounters.get(nodeName) + 1;
	currentGmlGeometryCounters.put(nodeName, newCounter);
    }

    private void resetGmlGeometryCounters() {
	for (String key : currentGmlGeometryCounters.keySet()) {
	    currentGmlGeometryCounters.put(key, 0);
	}
	LOGGER.trace("Counters reset");
    }

    /**
     * Checks that multiple polygon patches within a surface are connected.
     * <p>
     * The test is implemented as follows: Each polygon patch is converted into a
     * JTS Polygon. Then the union of all polygons is created. If the union geometry
     * is a JTS Polygon then the surface is connected - otherwise it is not.
     * <p>
     * Checks:
     * <ul>
     * <li>Surface (including PolyhedralSurface, CompositeSurface, and
     * OrientableSurface)</li>
     * <li>Only PolygonPatch is allowed as surface patch - all surfaces that contain
     * a different type of surface patch are ignored.</li>
     * <li>The elements of multi and composite geometries (except Multi- and
     * CompositeSolids).</li>
     * </ul>
     * Does NOT check the surfaces within solids!
     *
     * @param geom
     * @param jtsGeometry
     * @return <code>true</code> if the given geometry is a connected surface, a
     *         point, a curve, multi- or composite geometry that only consists of
     *         these geometry types, else <code>false</code>. Thus,
     *         <code>false</code> will be returned whenever a solid is encountered
     *         and if a surface is not connected.
     * @throws Exception
     */
    protected boolean checkConnectivityOfPolygonPatches(org.deegree.geometry.Geometry geom, Geometry jtsGeometry)
	    throws Exception {

	if (geom instanceof Surface) {

	    Surface s = (Surface) geom;

	    List<? extends SurfacePatch> sps = s.getPatches();

	    if (sps.size() <= 1) {

		// not multiple patches -> nothing to check
		return true;

	    } else {

		/*
		 * Determine the JTS geometry of the surface. It may be provided as a parameter.
		 * Otherwise it has to be computed.
		 */
		com.vividsolutions.jts.geom.Geometry g;
		if (jtsGeometry != null) {
		    g = jtsGeometry;
		} else {
		    /*
		     * Compute JTS geometry from the surface with multiple patches. An exception
		     * will be thrown if the surface does not consist of polygon patches.
		     */
		    try {
			g = geoutils.toJTSGeometry(geom);

		    } catch (UnsupportedGeometryTypeException e) {

			String gmlid = Dom4JHelper.findGmlId(currentElement);
			if (geom.getId() != null) {
			    gmlid = geom.getId();
			}
			if (gmlid == null) {
			    gmlid = "null";
			}

			validatorContext.addWarning(ValidatorMessageBundle.getMessage(
				"validator.core.validation.geometry.unsupportedgeometrytype", gmlid, e.getMessage()));
			return true;
		    }
		}
		/*
		 * If the surface is connected then the JTS geometry is a JTS Polygon (because a
		 * union of all patches has been created). Otherwise the surface is not
		 * connected.
		 */

		if (g instanceof com.vividsolutions.jts.geom.Polygon) {

		    return true;

		} else {

		    String gmlid = Dom4JHelper.findGmlId(currentElement);
		    if (geom.getId() != null) {
			gmlid = geom.getId();
		    }
		    if (gmlid == null) {
			gmlid = "null";
		    }

		    validatorContext.addError(ValidatorMessageBundle
			    .getMessage("validator.core.validation.geometry.surfacepatchesnotconnected", gmlid));
		    return false;
		}
	    }

	} else if (geom instanceof MultiSolid || geom instanceof CompositeSolid || geom instanceof Solid) {

	    return false;

	} else if (geom instanceof MultiGeometry || geom instanceof CompositeGeometry) {

	    // MultiSurface extends MultiGeometry
	    // CompositeSurface extends CompositeGeometry

	    @SuppressWarnings("rawtypes")
	    List l = (List) geom;

	    for (Object o : l) {
		org.deegree.geometry.Geometry g = (org.deegree.geometry.Geometry) o;
		if (!checkConnectivityOfPolygonPatches(g, null)) {
		    return false;
		}
	    }

	    return true;

	} else {

	    return true;
	}

    }

    /**
     * Checks that two consecutive points in a posList - within a CurveSegment - are
     * not equal.
     * <p>
     * Checks:
     * <ul>
     * <li>Curve (including CompositeCurve, LineString, OrientableCurve, Ring)</li>
     * <li>The following CurveSegment types: Arc, ArcString, CubicSpline,
     * GeodesicString, LineStringSegment</li>
     * <li>The exterior and interior rings of polygon patches (contained within
     * Surface, Polygon, PolyhedralSurface, TriangulatedSurface, Tin,
     * CompositeSurface, or OrientableSurface) - NOTE: all other types of surface
     * patches are currently ignored!</li>
     * <li>The elements of multi and composite geometries</li>
     * </ul>
     * Does NOT check curve segments within solids!
     *
     * @param geom the geometry that shall be tested
     * @return <code>true</code> if no repetition was detected (or if the geometry
     *         is a point, a solid, or consists of solids), else <code>false</code>
     */
    protected boolean checkNoRepetitionInCurveSegment(org.deegree.geometry.Geometry geom) {

	if (geom instanceof Curve) {

	    /* includes CompositeCurve, LineString, OrientableCurve, Ring */

	    Curve curve = (Curve) geom;

	    List<CurveSegment> segments = curve.getCurveSegments();

	    for (int segmentIdx = 0; segmentIdx < segments.size(); segmentIdx++) {

		CurveSegment segment = segments.get(segmentIdx);

		Points points = null;

		if (segment instanceof ArcString) {

		    ArcString as = (ArcString) segment;
		    points = as.getControlPoints();

		} else if (segment instanceof Arc) {

		    Arc arc = (Arc) segment;
		    List<Point> lp = new ArrayList<Point>();

		    lp.add(arc.getPoint1());
		    lp.add(arc.getPoint2());
		    lp.add(arc.getPoint3());

		    points = new PointsList(lp);

		} else if (segment instanceof CubicSpline) {

		    CubicSpline cs = (CubicSpline) segment;
		    points = cs.getControlPoints();

		} else if (segment instanceof GeodesicString) {

		    GeodesicString gs = (GeodesicString) segment;
		    points = gs.getControlPoints();

		} else if (segment instanceof LineStringSegment) {

		    LineStringSegment lss = (LineStringSegment) segment;
		    points = lss.getControlPoints();
		}

		Point lastPoint = null;
		for (Point point : points) {
		    if (lastPoint != null) {
			if (point.equals(lastPoint)) {

			    String s0 = Dom4JHelper.findGmlId(currentElement);
			    if (geom.getId() != null) {
				s0 = geom.getId();
			    }
			    if (s0 == null) {
				s0 = "null";
			    }

			    String s1 = ValidationUtil.getAffectedCoordinates(curve.getCurveSegments().get(segmentIdx),
				    null);
			    String s2 = ValidationUtil.getProblemLocation(point);
			    validatorContext.addError(ValidatorMessageBundle.getMessage(
				    "validator.core.validation.geometry.repetitionincurvesegment", s0, s1, s2));
			    return false;
			}
		    }
		    lastPoint = point;
		}
	    }

	    return true;

	} else if (geom instanceof Surface) {

	    Surface s = (Surface) geom;

	    List<? extends SurfacePatch> patches = s.getPatches();

	    for (SurfacePatch sp : patches) {

		if (sp instanceof PolygonPatch) {

		    PolygonPatch pp = (PolygonPatch) sp;

		    Ring exterior = pp.getExteriorRing();

		    if (!checkNoRepetitionInCurveSegment(exterior)) {
			return false;
		    }

		    List<Ring> interiorRings = pp.getInteriorRings();
		    for (Ring interiorRing : interiorRings) {
			if (!checkNoRepetitionInCurveSegment(interiorRing)) {
			    return false;
			}
		    }

		} else {
		    // TODO is another type of surface patch relevant?
		}
	    }

	    return true;

	} else if (geom instanceof MultiGeometry || geom instanceof CompositeGeometry) {

	    @SuppressWarnings("rawtypes")
	    List l = (List) geom;

	    for (Object o : l) {
		org.deegree.geometry.Geometry g = (org.deegree.geometry.Geometry) o;
		if (!checkNoRepetitionInCurveSegment(g)) {
		    return false;
		}
	    }

	    return true;

	} else {

	    return true;
	}
    }

    /**
     * @return <code>true</code> if there is no repetition in curve segments
     */
    public boolean isNoRepetitionInCurveSegments() {
	return noRepetitionInCurveSegments;
    }

    /**
     * @return <code>true</code> if the polygon patches are connected
     */
    public boolean arePolygonPatchesConnected() {
	return polygonPatchesAreConnected;
    }

    /**
     * @return <code>true</code> if the geometry is simple
     */
    public boolean isSimple() {
	return isSimple;
    }

    /**
     * @return <code>true</code> if the geometry is generally valid
     */
    public boolean isGenerallyValid() {
	return generallyValid;
    }
}
