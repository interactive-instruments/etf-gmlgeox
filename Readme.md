GML_element geometryElement# GML geometry library for BaseX

The library can be used by the [ETF BaseX test driver](https://github.com/interactive-instruments/etf-bsxtd) to validate GML geometries within XML documents, perform geometry operations and index GML geometries.

[![European Union Public Licence 1.2](https://img.shields.io/badge/license-EUPL%201.2-blue.svg)](https://joinup.ec.europa.eu/software/page/eupl)
[![Latest version](http://img.shields.io/badge/latest%20version-1.2.0-blue.svg)](http://services.interactive-instruments.de/etfdev-af/release/de/interactive_instruments/etf/bsxm/etf-gmlgeox/1.2.0/etf-gmlgeox-1.2.0-sources.jar)
[![Build Status](https://services.interactive-instruments.de/etfdev-ci/buildStatus/icon?job=etf-gmlgeox)](https://services.interactive-instruments.de/etfdev-ci/job/etf-gmlgeox/)
[![GmlGeoX javadoc](https://img.shields.io/badge/javadoc-GmlGeoX-green.svg)](http://etf-validator.github.io/etf-gmlgeox/javadoc/de/interactive_instruments/etf/bsxm/GmlGeoX.html)


&copy; 2020 European Union, interactive instruments GmbH. Licensed under the EUPL.

## About ETF

ETF is an open source testing framework for validating spatial data, metadata and web services in Spatial Data Infrastructures (SDIs). For documentation about ETF, see [http://docs.etf-validator.net](http://docs.etf-validator.net/).

Please report issues [in the GitHub issue tracker of the ETF Web Application](https://github.com/interactive-instruments/etf-webapp/issues).

ETF component version numbers comply with the [Semantic Versioning Specification 2.0.0](http://semver.org/spec/v2.0.0.html).

## Build information

The project can be build and installed by running the gradlew.sh/.bat wrapper with:
```gradle
$ gradlew build install
```

## Using the library in the BaseX GUI


### Installation and update

In order to install the GmlGeoX module in the BaseX GUI, go to Options -> Packages... -> Install... -> select the GmlGeoX.xar from the GmlGeoX release.

In order to update the module in the BaseX GUI, first delete it by going to Options -> Packages... -> select the package whose name includes 'GmlGeoX' -> Delete...

NOTE: If BaseX reports an error while deleting the  module, close BaseX and go to the BaseX install directory. In subdirectory "repo", manually delete the folder whose name contains "GmlGeoX". Restart BaseX.

Then install the new GmlGeoX version as described above.


### Use

The plugin can be used by importing the module https://modules.etf-validator.net/gmlgeox/2 in a query:

```xquery
import module namespace geox = 'https://modules.etf-validator.net/gmlgeox/2';

let $dbName := "TEST_GMLGEOX-000"
let $init := geox:init($dbName)
let $gml := db:open($dbName)

return geox:validateAndReport($gml)
```

This is a simple quickstart example, which assumes that the database TEST_GMLGEOX-000 is available and contains a single GML geometry.

For testing, such a database can be created using the following updating function call in the BaseX GUI:

```xquery
db:create("TEST_GMLGEOX-000", <gml:Point xmlns:gml='http://www.opengis.net/gml/3.2'><gml:pos>0 0</gml:pos></gml:Point>, "doc.xml")
```

NOTE: GmlGeoX only operates on XML data that has been stored in a BaseX database. It does not operate on XML that has dynamically been created in a query.

NOTE: The name of the BaseX database must have a three-digit suffix. For further details, see [Initialization](#initialization).


## Initialization

Before any other GmlGeoX functions are called in a query, function `geox:init(string databaseName)` must be called (once, and only once).

The function parameter identifies the database, in which the data that is being tested is stored.

NOTE: The name of the BaseX database must have a three-digit suffix, typically: '000'. That is a requirement which results from the internal database management of ETF.


## Geometry parsing and caching

In order to perform spatial computations on XML encoded spatial features, geometries of these features must be parsed by GmlGeoX. This section documents important aspects of geometry parsing with GmlGeoX, such as determination of the spatial reference system, linearization, and supported geometry types. The section also documents the geometry cache provided by GmlGeoX. The geometry cache is essential in order to prevent costly re-parsing of geometry elements, and is a key aspect for achieving good test performance.

NOTE: In this section as well as all following sections, GmlGeoX functions are written as follows: `geox:function(parameter_type parameter_name)`. In order to simplify the documentation of functions on this page, the parameter type used in function signatures does not define the exact XML element or Java object type. Instead, it merely describes the parameter type. Often, 'GML_element' is used as parameter type, which represents an XML element, more specifically a Geography Markup Language (GML) encoded geometry. A 'JTS_geometry' as parameter type represents a Java object, more specifically a JTS geometry object, which has either been parsed by GmlGeoX from some GML element, or created using some GmlGeoX function. In plural, e.g. 'GML_elements', a sequence of such geometries is meant.

NOTE: All available GmlGeoX functions are documented in detail both in the [XQuery module descriptor file](https://github.com/interactive-instruments/etf-gmlgeox/blob/EIPs/EIP-57/src/main/xquery/GmlGeoX.xq), and in the JavaDoc of public methods from [GmlGeoX.java](https://github.com/interactive-instruments/etf-gmlgeox/blob/EIPs/EIP-57/src/main/java/de/interactive_instruments/etf/bsxm/GmlGeoX.java).


### Geometry parsing

Function `geox:getOrCacheGeometry(GML_element geometryElement)` parses a GML element, creates (and returns) a JTS geometry object, und stores that object in the [geometry cache](#geometry-caching).

NOTE: In order to correctly parse geometries with 3D coordinates, it is essential that the spatial reference system that applies to the geometry be correctly determined. For further details, see [determining the spatial reference system](#determining-the-spatial-reference-system).

NOTE: The GML geometry types that can be parsed by GmlGeoX are documented in section [Supported geometry types](#supported-geometry-types).

Many GmlGeoX functions use JTS geometries as parameter or return JTS geometries. In order to, for example, check if a dynamically computed union of feature geometries (A) spatially intersects with the geometry of another feature (B), the geometry of feature B must be available as JTS geometry object. In that case, the geometry of B must explicitly be parsed before the intersection can be computed.

Example:

```xquery
let $unionOtherGeometriesA := geox:union($featuresA/*:position/*)
let $featureBGeometry := geox:getOrCacheGeometry($featureB/*:position/*)
let $intersects := geox:intersectsGeomGeom($featureBGeometry,$unionOtherGeometriesA,false())
```

NOTE: Further details on the signature of `geox:intersectsGeomGeom`, are given in the section on [evaluation of topological predicates](#evaluation-of-topological-predicates).


#### Determining the spatial reference system

When GmlGeoX parses a GML encoded geometry, the spatial reference system (SRS) that applies to that geometry is determined. Knowledge about the SRS is essential for correctly parsing the coordinates of the geometry, because the SRS defines the number of coordinate axes, and thereby specifies if the geometry is defined using 2D or 3D geometries.

GmlGeoX determines the spatial reference system of a GML geometry element as follows:

1. If the GML element has the XML attribute 'srsName', then the attribute value identifies the SRS.
   * NOTE: The SRS identifier is used to look up the SRS definition within the [GmlGeoX internal SRS configuration](https://github.com/interactive-instruments/etf-gmlgeox/tree/EIPs/EIP-57/src/main/resources/srsconfig).
2. Otherwise, the standard SRS is used - if one is set.
   * A standard SRS can be set by calling function `geox:setStandardSRS(string srsName)`. If a standard SRS applies to a given test dataset (which depends on the use case and data format), it should be set early on in a test run, before geometries are parsed or indexed.
3. Otherwise, the so-called ancestors of the GML element are inspected, to see if one of them has the 'srsName' XML attribute. If so, then the SRS defined by the nearest ancestor will be used.
4. Otherwise, the ancestors of the GML element will be reviewed again, but this time in order to determine if one of them has a GML envelope with 'srsName' as child element. If so, then the according SRS of the nearest ancestor is used.

NOTE: If no standard SRS is set, and the SRS of GML geometry elements in the test dataset is defined by ancestors, rather than being directly defined using the 'srsName' XML attribute on the elements themselves, then this would have a significant negative impact on test performance (due to the steps 3. and 4. described above being executed whenever a geometry element is parsed).

NOTE: The GmlGeoX function `geox:determineSrsName(GML_element geometry)` can be used to query the SRS of a GML geometry element. This can be useful for debugging.


#### Supported geometry types

The implementation of GmlGeoX depends to a large extent on the deegree framework. The default geometry implementation of deegree does not support parsing all GML types, primarily the GML 3.3 types. Also, in a number of cases, spatial operations are not supported for parsed geometries. This is primarily due to the fact that deegree relies on JTS to perform these operations, and that therefore the geometries must be simplified/linearized - which is not implemented for all geometry types that can be parsed by deegree.

Long story short, what this tells us is that a developer of this module must be very careful regarding which geometry types are supported for processing.

The following geometry types **are** supported by GmlGeoX:

* GM_Point
* Curves:
  * GM_Curve
  * GM_Ring
  * GM_LinearRing
  * GM_LineString
  * GM_OrientedCurve  
* Curve segments:
  * GM_Arc (will be linearized)
  * GM_Circle (will be linearized)
  * GM_LineString
  * GM_CubicSpline (will be linearized)
  * GM_ArcString (will be linearized)  
* Surfaces:
  * GM_Surface
  * GM_PolyhedralSurface
  * GM_OrientableSurface
* Surface segments:
  * GM_Polygon
* Aggregates:
  * GM_Composite
  * GM_CompositePoint
  * GM_CompositeCurve
  * GM_CompositeSurface  
* Multi geometries:
  * GM_Aggregate
  * GM_MultiPoint
  * GM_MultiCurve
  * GM_MultiSurface

The following geometry types are **not** supported:

* GM_Solid
* Curve segments:
  * GM_ArcByBulge
  * ArcByCenterPoint
  * GM_ArcStringByBulge
  * GM_Bezier
  * GM_BSplineCurve
  * CircleByCenterPoint
  * GM_Clothoid
  * GM_Geodesic
  * GM_GeodesicString
  * GM_OffsetCurve
  * GM_Conic
* Surfaces:
  * GM_TriangulatedSurface
  * GM_Tin
  * GM_ParametricCurveSurface
  * GM_GriddedSurface
  * GM_BilinearGrid
  * GM_BicubicGrid
  * GM_Cone
  * GM_Cylinder
  * GM_Sphere
  * GM_BSplineSurface
* Aggregates:
  * GM_CompositeSolid
* Multi geometries:
  * GM_MultiSolid

As noted above, some of the supported geometry types will be linearized when parsed. What this means, as well as configuration options, are documented in the [Linearization](#linearization) section.


#### Linearization

Spatial computations are performed by GmlGeoX using JTS. JTS does not support non-linear geometries. Thus, the non-linear geometries - more specifically: curve segments - that are supported by GmlGeoX will be linearized during parsing and conversion to a JTS geometry object.

By default, a non-linear curve segment is linearized by converting the segment to a line string segment, adding intermediate control points to approximate the non-linear curve.

The linearization done by GmlGeoX can be configured in two ways: by setting a maximum error, and by setting a maximum number of control points.

The maximum error (default setting is 0.00001) is the maximum difference between the non-linear curve and the interpolated line string, that shall be achieved when creating new arc interpolations. It can be set using function `geox:setMaxErrorForInterpolation(positive_double maxError)`. The lower the error (maximum difference), the more interpolation points will be needed. However, note that a maximum for the number of such points exists. It can be set via function `geox:setMaxNumPointsForInterpolation(positive_integer maxNumPoints)` (default is 1000). Again: linearization will never create more than the configured maximum number of points. However, the interpolation will also never create more points than needed to achieve the maximum error. In order to achieve interpolations with a very low maximum error, the maximum number of points needs to be increased accordingly.

WARNING: If you intend to achieve a linearization with a very low maximum error (by setting the according parameter), you will need to set the maximum number of points to a very high number. The resulting linearized geometries will then have a very high number of control points, which will significantly decrease the performance of spatial computations, and thus of the tests that require these computations.

Another aspect that needs to be considered is that if you want to compare linearized geometries using, for example, a topological predicate, then both of these geometries should have been linearized with the same settings for maximum error and number of control points.

Also consider that whenever a geometry is parsed, it will be added to the [geometry cache](#geometry-caching). The only exception is parsing during [geometry validation](#geometry-validation). Now think of the following scenario: A geometry A is linearized with a high level of detail, and added to the cache. Then the level of detail for linearization is decreased (increasing the maximum error, and decreasing the maximum number of control points). Afterwards, a geometry B is linearized and added to the geometry cache. Then a test shall be made, if geometry A overlaps geometry B. Since geometry A is still stored in the geometry cache, it will not be parsed again. You will end up comparing two geometries which have been linearized with a different level of detail. The only way to ensure that you do not end up comparing geometries that have been linearized differently is to reset the geometry cache (see [geometry caching](#geometry-caching) on how that can be done).

NOTE: One use case where it makes sense to perform a highly detailed linearization is [geometry validation](#geometry-validation). Once validation has been completed, you would set the maximum error and maximum number of control points back to their default values, or at least to values that result in a lower level of detail for subsequent linearizations.


### Geometry caching

Whenever a GML geometry element is parsed by GmlGeoX - the only exception being [geometry validation](#geometry-validation) - the resulting JTS geometry object is automatically added to the internal geometry cache. Before parsing a GML geometry element, GmlGeoX checks the geometry cache to see if it contains a JTS geometry object for that element. If so, the cached geometry object will be used, and the GML element will not be parsed. The geometry cache allows GmlGeoX to significantly reduce the number of times that a GML geometry element needs to be parsed during a test run.

The default size of the geometry cache is 100000. The  cache size can be changed using function `geox:cacheSize( integer size )`. Whenever this function is called, GmlGeoX will reset the geometry cache, and throw away all previously stored geometry objects. That can be useful if the [linearization](#linearization) criteria have been changed.

NOTE: Function `geox:getCacheSize()` will return the size of the geometry cache, i.e. the maximum size - not the number of currently cached objects.


## Geometry validation

Validation of a GML geometry element is basically a SAX-based scan for recognized GML elements, and subsequent validation of these elements.

The default set of recognized element names is a subset of GML. It contains the names: Point, Polygon, Surface, Curve, LinearRing, MultiPoint, MultiPolygon, MultiGeometry, MultiSurface, MultiCurve, Ring, and LineString. If the (local) name of the GML element is not contained in the set of recognized element names, it will not be validated.

The following functions are available to inspect and to modify the set of geometry element names registered for validation:

* `geox:registeredGmlGeometries()`
* `geox:registerGmlGeometry(string gmlGeometryElementName)`
* `geox:unregisterGmlGeometry(string gmlGeometryElementName)`
* `geox:unregisterAllGmlGeometries()`

Geometry validation consists of a number of validation tasks. Currently, the following tasks are available:

1. General Validation - This test validates the given geometry using the validation functionality of both deegree and JTS. More specifically:
   * **deegree based validation:**
      * primitive geometry (point, curve, ring, surface):
         * point: no specific validation
         * curve:
            * duplication of successive control points (only for linear curve segments)
            * segment discontinuity
            * self intersection (based on JTS isSimple())
         * ring:
            * Same as curve.
            * In addition, test if ring is closed
         * surface:
            * only checks PolygonPatch, individually:
            * applies ring validation to interior and exterior rings
            * checks ring orientation (ignored for GML 3.1):
               * must be counter-clockwise for exterior ring
               * must be clockwise for interior ring
            * interior ring intersects exterior
            * interior ring outside of exterior ring
            * interior rings intersection
            * interior rings are nested
      * composite geometry: member geometries are validated individually
      * multi geometry: member geometries are validated individually
      * NOTE: There's some overlap with JTS validation. The following invalid situations are reported by the JTS validation:
         * curve self intersection
         * interior ring intersects exterior
         * interior ring outside of exterior ring
         * interior rings intersection
         * interior rings are nested
         * interior rings touch
         * interior ring touches exterior
   * **JTS based validation:**
      * Point:
         * invalid coordinates
      * LineString:
         * invalid coordinates
         * too few points
      * LinearRing:
         * invalid coordinates
         * closed ring
         * too few points
         * no self intersecting rings
      * Polygon:
         * invalid coordinates
         * closed ring
         * too few points
         * consistent area
         * no self intersecting rings
         * holes in shell
         * holes not nested
         * connected interiors
      * MultiPoint:
         * invalid coordinates
      * MultiLineString:
         * Each contained LineString is validated on its own.
      * MultiPolygon:
         * Per polygon:
               * invalid coordinates
               * closed ring
               * holes in shell
               * holes not nested
         * too few points
         * consistent area
         * no self intersecting rings
         * shells not nested
         * connected interiors
      * GeometryCollection:
         * Each member of the collection is validated on its own.
      * General description of checks performed by JTS:
         * invalid coordinates: x and y are neither NaN or infinite
         * closed ring: tests if ring is closed; empty rings are closed by definition
         * too few points: tests if length of coordinate array - after repeated points have been removed - is big enough(e.g. >= 4 for a ring, >= 2 for a line string)
         * no self intersecting rings: Check that there is no ring which self-intersects (except of course at its endpoints); required by OGC topology rules
         * consistent area: Checks that the arrangement of edges in a polygonal geometry graph forms a consistent area. Includes check for duplicate rings.
         * holes in shell: Tests that each hole is inside the polygon shell (i.e. hole rings do not cross the shell ring).
         * holes not nested: Tests that no hole is nested inside another hole.
         * connected interiors: Check that the holes do not split the interior of the polygon into at least two pieces.
         * shells not nested: Tests that no element polygon is wholly in the interior of another element polygon (of a MultiPolygon).
2. Polygon Patch Connectivity - Checks that multiple polygon patches within a single surface are connected.
3. Repetition of Position in CurveSegments - Checks that consecutive positions within a CurveSegment are not equal.
4. isSimple - Tests whether a geometry is simple, based on JTS Geometry.isSimple(). In general, the OGC Simple Features specification of simplicity follows the rule: A Geometry is simple if and only if the only self-intersections are at boundary points.
Simplicity is defined for each JTS geometry type as follows:
   * Polygonal geometries are simple if their rings are simple (i.e., their rings do not self-intersect).
      * Note: This does not check if different rings of the geometry intersect, meaning that isSimple cannot be used to fully test for (invalid) self-intersections in polygons. The JTS validity check fully tests for self-intersections in polygons, and is part of the general validation in GmlGeoX.
   * Linear geometries are simple iff they do not self-intersect at points other than boundary points.
   * Zero-dimensional (point) geometries are simple if and only if they have no repeated points.
   * Empty geometries are always simple, by definition.

Geometry validation can be invoked for a GML geometry element using one of the following functions:

   * `geox:validate(GML_element geometryElement)`
   * `geox:validate(GML_element geometryElement, string test_mask)`
   * `geox:validateAndReport(GML_element geometryElement)`
   * `geox:validateAndReport(GML_element geometryElement. string test_mask)`

The differences between these functions is as follows:

* Set of validation task being executed:
   * The functions with a single parameter run all geometry validation tasks.
   * For the other two functions, the set of validation tasks to run is defined using the second function parameter, the test mask. The mask is a simple string, where the character '1' at the position of a specific test (assuming a 1-based index) specifies that the test shall be performed. If the mask does not contain a character at the position of a specific test (because the mask is empty or the length is smaller than the position), then the test will be executed. Examples:
      * The mask '0100' indicates that only the 'Polygon Patch Connectivity' test shall be performed.
      * The mask '1110' indicates that all tests except the 'isSimple' test shall be performed .
* Return type:
   * The `geox:validate(..)` functions only return a string with the test results, encoded as characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVFF' shows that the first test was skipped, while the second test passed and the third and fourth failed.
   * The `geox:validateAndReport(..)` functions return an XML element, as follows:

```xml
	 <ggeo:ValidationResult xmlns:ggeo="de.interactive_instruments.etf.bsxm.GmlGeoX">
	  <ggeo:valid>false</ggeo:valid>
	  <ggeo:result>VFV</ggeo:result>
	  <ggeo:errors>
	   <etf:message xmlns:etf="http://www.interactive-instruments.de/etf/2.0" ref="TR.gmlgeox.validation.geometry.jts.5">
	    <etf:argument token="original">Invalid polygon. Two rings of the polygonal geometry intersect.</etf:argument>
	    <etf:argument token="ID">DETHL56P0000F1TJ</etf:argument>
	    <etf:argument token="context">Surface</etf:argument>
	    <etf:argument token="coordinates">666424.2393405803,5614560.422015165</etf:argument>
	   </etf:message>
	  </ggeo:errors>
	 </ggeo:ValidationResult>
```
Where:
* ggeo:valid - contains the boolean value indicating if the object passed all tests (defined by the test mask).
* ggeo:result - contains a string that is a mask with the test results, encoded as characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped.
* ggeo:message (one for each message produced during validation) contains:
   * an XML attribute 'type' that indicates the severity level of the message ('FATAL', 'ERROR', 'WARNING', or 'NOTICE')
   * the actual validation message as text content


## Creating and using spatial indexes

Feature geometries can be indexed using an R*-tree. It is strongly recommended to build spatial indexes at the start of a test run, in order to enable spatial searches, and thus to increase test performance.

GmlGeoX can manage multiple spatial indexes, which are identified by name. The default spatial index has no name / the empty string as name. Here are some examples how you can use spatial indexes:

* You can build a spatial index per feature type. This would be useful for searching features that are of the same type as a given feature.
* You can use the default spatial index to index all features (of different types) that are contained in the test dataset. This can be useful for searches where features of different types need to be looked up at the same time.
* If a feature type has multiple geometry properties, you can even create a spatial index for each of these properties. This would be useful if you need to perform searches based upon different geometry characteristics of a feature.

The following functions are used to index a GML geometry element:

* `geox:index(XML_element someElement, GML_element geometryElement)` - Adds some element, typically representing a feature, to the default spatial index, with the spatial extent defined by the given GML element (essentially: the bounding box of the represented geometry).
* `geox:index(string indexName, XML_element someElement, GML_element geometryElement)` - The same as before, but adding the element to the spatial index with given name (the default index, if that name is the empty string).

Once all relevant elements have been indexed, you need to instruct GmlGeoX to actually build the spatial indexes, using the following functions:

* `geox:buildSpatialIndex()` - Builds the default spatial index.
* `geox:buildSpatialIndex(string indexName)` - Builds the named spatial index (the default index, if the name is the empty string).

NOTE: Behind the scene, the spatial indexes will be built using bulk loading. According to https://github.com/ambling/rtree-benchmark, creating an R*-tree using bulk loading is faster than doing so without bulk loading. Furthermore, according to https://en.wikipedia.org/wiki/R-tree, an STR bulk loaded R*-tree is a "very efficient tree".

NOTE: It is essential that the XML database is not updated after the spatial indexes have been built. Otherwise the database index structures would change and internal references would become obsolete.

Here is an example that shows how a set of features can be indexed, using the default spatial index):

```xquery
let $dummy :=
  for $feature in $features
  return geox:index($feature,$feature/*:position/*)
let $dummyBuildSpatialIndex := geox:buildSpatialIndex()
```

Once the index has been established, it can be searched to find all elements whose bounding box intersects with a given bounding box. The following functions can be used for searching in spatial indexes:

* `ggeo:search(double minx, double miny, double maxx, double maxy )` - Searches in the default spatial index, with the search window defined by the four parameter values.
* `ggeo:search(string indexName, double minx, double miny, double maxx, double maxy )` - As before, but searches in a named spatial index.
* `ggeo:search(GML_element geometryElement)` - Searches in the default spatial index, with the search window defined by the bounding box of the geometry that is represented by the given geometry element.
* `ggeo:search(string indexName, GML_element geometryElement)` - As before, but searches in a named spatial index.
* `ggeo:searchGeom(JTS_geometry geometryObject)` - Searches in the default spatial index, with the search window defined by the bounding box of the given JTS geometry object.
* `ggeo:searchGeom(string indexName, JTS_geometry geometryObject)` - As before, but searches in a named spatial index.

**Examples**

Search in the default spatial index:

```xquery
let $resultingFeatures := geox:search($someFeature/*:position/*)
```

Search in a named spatial index:

```xquery
let $buildingFeatures := geox:search('Building',$someFeature/*:position/*)
```

Search in multiple named spatial indexes (for buildings and roads):

```xquery
let $resultingFeatures := ('Building','Road') ! geox:search(.,$someFeature/*:position/*)
```


## Evaluation of topological predicates

GmlGeoX supports computation of the topological predicates defined by the OGC standard [Simple Feature Access - Part 1: Common Architecture](http://portal.opengeospatial.org/files/?artifact_id=25355):
_contains, crosses, equals, intersects, isDisjoint, isWithin, overlaps, touches_. In addition, GmlGeoX supports the operator _relate_, covering the computation of specific topological relations.

Each of these predicates can be checked by calling one of four functions. The signature of these functions differs. For example, for the predicate _intersects_, the functions are as follows:

* `intersects(GML_element geom1, GML_element geom2)` - The geometries to compare are given as GML elements. None of these may be a collection or sequence - otherwise, the following function with same name, but with the additional parameter 'matchAll', should be used.
* `intersectsGeomGeom(JTS_geometry geom1, JTS_geometry geom2)` - As before, just with JTS geometry objects as parameter values.
* `intersects(GML_elements geom1, GML_elements geom2, boolean matchAll)` - The geometries to compare are single or collections/sequences of GML elements. If the parameter 'matchAll' is true, the predicate will only be true if all geometries of the first parameter intersect with all geometries of the second parameter. Otherwise, only a single pair of geometries from the first and the second parameter needs to intersect. When comparing multiple geometries, controlling the desired behavior via the 'matchAll' parameter is essential. Typically, only a single geometry is given as first parameter value, and the topological relationship of this geometry to a set of other geometries (given as value of the second parameter) is computed.
* `intersectsGeomGeom(JTS_geometries geom1, JTS_geometries geom2, boolean matchAll)` - As before, just with JTS geometry objects as parameter values.

Similar functions are available for the _relate_ operator. These functions have an additional parameter, which defines the topological relationship that must be fulfilled via a DE-9IM matrix string with nine components (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE), where 'I' means interior, 'B' means boundary, and 'E' means exterior. Each component is either  '0', '1', '2', '\*', 'T', or 'F'. For further details, see [OGC Simple Feature Access - Part 1: Common Architecture](http://portal.opengeospatial.org/files/?artifact_id=25355), section 6.1.15.2.

In the following example, spatial overlap is determined using the  `geox:overlaps` function:

```xquery
let $featuresWithErrors :=
  (for $candidate in $features
  let $candidate_geometryElement := $candidate/*:position/*
  let $other_features := geox:search($candidate_geometryElement) except $candidate
  return
    if(geox:overlaps($candidate_geometryElement,$other_features/*:position/*,false())) then
      $candidate
    else
      ()
  )[position() le $limitErrors]
```


## Additional GmlGeoX functions

Additional functions supported by GmlGeoX include, but are not limited to:

* Compute the boundary of a geometry:
  * `geox:boundary(GML_element geometryElement)`
  * `geox:boundaryGeom(JTS_geometry geometryObject)`
* Compute the union of multiple geometries:
  * `geox:union(GML_elements geometryElements)`
  * `geox:unionGeom(JTS_geometries geometryObjects)`
* Compute the intersection of two geometries:
  * `geox:intersection(GML_element geometryElement1, GML_element geometryElement2)`
  * `geox:intersectionGeomGeom(JTS_geometry geometryObject1, JTS_geometry geometryObject2)`
* Compute the difference of two geometries:
  * `geox:difference(GML_element geometryElement1, GML_element geometryElement2)`
  * `geox:differenceGeomGeom(JTS_geometry geometryObject1, JTS_geometry geometryObject2)`
* Compute the holes in a JTS geometry (typically a surface):
  * `geox:holes(GML_element geometryElement)`
  * `geox:holesGeom(JTS_geometry geometry)`
  * `geox:holesAsGeometryCollection(JTS_geometry geometry)`  
* Check if the control points of a circle have a minimum distance (in degree, 0<=x<=120) to each other: `geox:checkMinimumSeparationOfCircleControlPoints(GML_element circleElement, number minimumDistance)`
* Check if the second control point of an arc is in the middle third of the arc: `geox:checkSecondControlPointInMiddleThirdOfArc(GML_element arcElement)`
* Determine the points of a given line (represented as JTS LineString or JTS MultiLineString), at which the line segment that ends in such a point, and the following segment that starts with that point, form a directional change within a given interval (0 <= minAngle <= maxAngle <= 180): `geox:directionChanges(JTS_geometry geometry, number minAngle, number maxAngle)`
* Determine the points of a given line (represented as JTS LineString or JTS MultiLineString), at which the line segment that ends in such a point, and the following segment that starts with that point, form a directional change that is bigger than a given limit (0 <= limitAngle <= 180): `geox:directionChangesGreaterThanLimit(JTS_geometry geometry, number limitAngle)`
* Compute the endpoints of a given curve: `geox:curveEndPoints(GML_element geometryElement)`. The result is a sequence of JTS Point objects.
* Functions useful for debugging:
   * Compute the first two coordinates:
      * of a JTS geometry: `geox:georefFromGeom(JTS_geometry geometry)`
      * of a JTS Coordinate object: `geox:georefFromCoord(JTS_Coordinate coord)`
   * Check if a JTS geometry is empty: `geox:isEmptyGeom(JTS_geometry geometry)`.
   * Compute the Well-Known-Text (WKT) representation of a JTS geometry: `geox:toWKT(JTS_geometry geometry)`.

NOTE: All available GmlGeoX functions are documented in detail both in the [XQuery module descriptor file](https://github.com/interactive-instruments/etf-gmlgeox/blob/EIPs/EIP-57/src/main/xquery/GmlGeoX.xq), and in the JavaDoc of public methods from [GmlGeoX.java](https://github.com/interactive-instruments/etf-gmlgeox/blob/EIPs/EIP-57/src/main/java/de/interactive_instruments/etf/bsxm/GmlGeoX.java).


## Graph based operations

The GmlGeoX distribution contains another module, for graph based operations, called GraphX.

NOTE: The GraphX module may be distributed separately in the future.

GraphX currently offers functions to build a graph, and to compute the maximally connected sets in that graph. That can be useful to check if a set of features is connected (i.e. there is a path from every feature to every other feature), or not. A use case would be to check the connectedness of the features that form a water network.

The module can be imported as follows:

```xquery
import module namespace graphx = 'https://modules.etf-validator.net/graphx/1';
```

The first step is to initialize the module, much [like it is done for GmlGeoX](#initialization), by calling function `graphx:init`.

Then a graph can be constructed. First, all features that represent nodes need to be added to the graph, using function `graphx:addVertexToSimpleGraph`. Afterwards, edges can be defined: If two features are connected - for example if two features with a curve geometry have the same endpoint - then function `graphx:addEdgeToSimpleGraph` can be used to add an edge between the nodes in the graph that represent these features.

Once the graph has been constructed, function `graphx:determineConnectedSetsInSimpleGraph` can be called in order to compute the set of maximally connected subgraphs. If only a single such graph exists, all features are connected. Otherwise, there are two or more clusters of features that are not connected to each other.

NOTE: All available GraphX functions are documented in detail both in the [XQuery module descriptor file](https://github.com/interactive-instruments/etf-gmlgeox/blob/EIPs/EIP-57/src/main/xquery/GraphX.xq), and in the JavaDoc of public methods from [GraphX.java](https://github.com/interactive-instruments/etf-gmlgeox/blob/EIPs/EIP-57/src/main/java/de/interactive_instruments/etf/bsxm/GraphX.java).


## Developer notes:

### Deterministic vs. Non-Deterministic Functions

When exposing a Java method as an XQuery function through a module, BaseX offers a way to indicate if the according function is deterministic. By default, such a function is assumed to be non-deterministic. Deterministic functions allow optimization, i.e. caching results instead of re-evaluating a function each time it occurs in the query execution.

As stated on the [BaseX Wiki](http://docs.basex.org/wiki/Java_Bindings), **only** indicate that a Java method is deterministic if you know that it will have no side-effects and will always yield the same result. For the spatial relationship operators, for example, this is the case.
