# Gml geometry validation library

[![Apache License 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Latest version](http://img.shields.io/badge/latest%20version-1.1.0-blue.svg)](https://services.interactive-instruments.de/etfdev-af/etf-public-dev/de/interactive_instruments/etf/bsxm/etf-gmlgeox/1.1.0-SNAPSHOT/etf-gmlgeox-1.1.0-20170112.165838-2.jar)
[![Build Status](https://services.interactive-instruments.de/etfdev-ci/buildStatus/icon?job=etf-gmlgeox)](https://services.interactive-instruments.de/etfdev-ci/job/etf-gmlgeox/)

The library can be used by the [ETF BaseX test driver](https://github.com/interactive-instruments/etf-bsxtd) to validate GML geometries within XML documents.

Please use the [etf-webapp project](https://github.com/interactive-instruments/etf-webapp) for
reporting [issues](https://github.com/interactive-instruments/etf-webapp/issues) or
[further documentation](https://github.com/interactive-instruments/etf-webapp/wiki).

The project can be build and installed by running the gradlew.sh/.bat wrapper with:
```gradle
$ gradlew build install
```

ETF component version numbers comply with the [Semantic Versioning Specification 2.0.0](http://semver.org/spec/v2.0.0.html).

ETF is an open source test framework developed by [interactive instruments](http://www.interactive-instruments.de/en) for testing geo network services and data.

## Installation in BaseX 8.6 (IDE used for developing executable test suites)
[Download and install BaseX 8.6](http://basex.org/products/download/all-downloads).

Click on the batch "latest version" in this repository to download the GmlGeoX plugin. In BaseX click on
Options -> Packages... -> Install... and select the downloaded file. The package name
de.interactive_instruments.etf.bsxm.GmlGeoX should appear in the package dialog, if it has been successfully installed.

The plugin can be used by importing the module **de.interactive_instruments.etf.bsxm.GmlGeoX**:

```xquery
import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

let $gml := fn:parse-xml(
"<gml:posList xmlns:gml='http://www.opengis.net/gml/3.2' count='2'>0 0 1 0</gml:posList>")

return ggeo:validateAndReport($gml)
```

This is a simple quickstart example, see the Wiki for further information!

## Updating
Uninstall the package Options -> Packages... -> select de.interactive_instruments.etf.bsxm.GmlGeoX -> Delete... and install the new version as described above.

## Geometry Support
The implementation of the module depends to a large extent on the deegree framework. The default geometry implementation of deegree does not support parsing all GML types, primarily the GML 3.3 types. Also, in a number of cases spatial operations are not supported for parsed geometries. This is primarily due to the fact that deegree relies on JTS to perform these operations, and that therefore the geometries must be simplified/linearized - which is not implemented for all geometry types that can be parsed by deegree. There is even a case where an incomplete JTS representations of a deegree geometry is accepted and used for spatial operations (for a surface with multiple polygon patches only the first is used)!

NOTE: Within this module, GML geometry elements are parsed by deegree, and then converted to JTS geometries via method `toJTSGeometry(Geometry)`in `GmlGeoXUtils.java` (at least for the spatial relationship operators).

Long story short, what this tells us is that a developer of this module must be very careful regarding which geometry types are supported for processing, and whether the computation is robust and exact or not. The documentation of a module function in both the source code itself (via Javadoc) and the test project developer manual should define the restrictions imposed by the implementation of the function.

For the spatial relationship operators, [this page](https://github.com/interactive-instruments/etf-webapp/wiki/gmlgeox-module-geometry-types-supported-by-spatial-operators) in the test project developer manual documents which geometry types are supported (also if they would be simplified/linearized) and which are not.

## Geometry Validation

Validation of GML geometry elements within a given XML node is basically a SAX-based scan for recognized GML geometry elements, and subsequent validation of these elements. The default set of recognized element names is a subset of GML. Functions offered by the module can be used to modify this set within an XQuery. See the [test project developer documentation for this module](https://github.com/interactive-instruments/etf-webapp/wiki/dev_manual_modules_gmlgeox) for further details.

## Indexing

Feature geometries can be indexed using an r*-tree. To index a feature execute `ggeo:index( int pre, String dbName, String id, Node xmlGeometry )`. 'pre' is the index of the feature node in the database table that can be obtained using `db:node-pre($node)`. It is essential that the XML database is not updated as this will change the pre values. 'dbName' is the name of the database that contains the feature node and can be obtained using `db:name($node)`. The 'id' is a String id used for the geometry cache. Typically the gml:id of the feature is used. The 'xmlGeometry' is the XML node with the GML geometry element to index.

To index a node list of features (`$features`) simply execute (where 'ns:geometry' is the geometry property):

```
let $dummy := for $feature in $features
	return ggeo:index(db:node-pre($feature),db:name($feature),$feature/@gml:id,$feature/ns:geometry/*[1])
```

Once the index has been established, it can be searched to find all features whose bounding box overlaps with another bounding box. `ggeo:search( minx, miny, maxx, maxy )` returns a node list of indexed features overlapping with the search bounding box. For example:

```
let $env := ggeo:envelope($candidate_geometry)
let $overlapping_features := ggeo:search($env[1],$env[2],$env[3],$env[4])
```

Or, if you want the candidate geometries that might intersect the search bounding box:

```
let $geometries :=
	for $feature in ggeo:search($env[1],$env[2],$env[3],$env[4])
		return ggeo:getGeometry($feature/@gml:id,$feature/ns:geometry/*[1])
```

## Geometry caching

JTS geometries are cached during indexing to avoid multiple computation of the geometries from the XML. The cache size can be set before the indexing is started using `ggeo:cacheSize( int size )`, the default size is 100000 geometries. Geometries are accessed using `geo:getGeometry( String id, Node xmlGeometry )`. The `id` is specified during the indexing, typically the gml:id attribute of the GML feature is used. If the geometry with the id is currently in the cache, it is returned. Otherwise the geometry is computed from the XML and put into the cache.

## Deterministic vs. Non-Deterministic Functions

When exposing a Java method as an XQuery function through a module, BaseX offers a way to indicate if the according function is deterministic. By default, such a function is assumed to be non-deterministic. Deterministic functions allow optimization, i.e. caching results instead of re-evaluating a function each time it occurs in the query execution.

As stated on the [BaseX Wiki](http://docs.basex.org/wiki/Java_Bindings), **only** indicate that a Java method is deterministic if you know that it will have no side-effects and will always yield the same result. For the spatial relationship operators, for example, this is the case.
