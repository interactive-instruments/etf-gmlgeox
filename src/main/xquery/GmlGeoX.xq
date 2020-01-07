(:~
 :
 : ---------------------------------------
 : GmlGeoX XQuery Function Library Facade
 : ---------------------------------------
 :
 : Copyright (C) 2018-2020 interactive instruments GmbH
 :
 : Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 : the European Commission - subsequent versions of the EUPL (the "Licence");
 : You may not use this work except in compliance with the Licence.
 : You may obtain a copy of the Licence at:
 :
 : https://joinup.ec.europa.eu/software/page/eupl
 :
 : Unless required by applicable law or agreed to in writing, software
 : distributed under the Licence is distributed on an "AS IS" basis,
 : WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 : See the Licence for the specific language governing permissions and
 : limitations under the Licence.
 :
 : @author  Johannes Echterhoff (echterhoff aT interactive-instruments doT de)
 : @author  Clemens Portele ( portele aT interactive-instruments doT de )
 : @author  Jon Herrmann ( herrmann aT interactive-instruments doT de )
 : @author  Christoph Spalek (spalek aT interactive-instruments doT de)
 :
 :)

module namespace geox = 'https://modules.etf-validator.net/gmlgeox/2';
import module namespace java = 'java:de.interactive_instruments.etf.bsxm.GmlGeoX';

declare function geox:init($databaseName as xs:string) as empty-sequence() {
    java:init($databaseName)
};

declare function geox:init($databaseName as xs:string, $configurationDirectoryPathName as xs:string) as empty-sequence() {
    java:init($databaseName, $configurationDirectoryPathName)
};

declare function geox:toWKT($geom ) as xs:string {
    java:toWKT($geom)
};

declare function geox:flattenAllGeometryCollections($geom )  {
    java:flattenAllGeometryCollections($geom)
};

declare function geox:directionChanges($geom , $minAngle , $maxAngle )  {
    java:directionChanges($geom, $minAngle, $maxAngle)
};

declare function geox:directionChangesGreaterThanLimit($geom , $limitAngle )  {
    java:directionChangesGreaterThanLimit($geom, $limitAngle)
};

declare function geox:validate($node ) as xs:string {
    java:validate($node)
};

declare function geox:validate($node , $testMask as xs:string) as xs:string {
    java:validate($node, $testMask)
};

declare function geox:validateAndReport($node )  {
    java:validateAndReport($node)
};

declare function geox:validateAndReport($node , $testMask as xs:string)  {
    java:validateAndReport($node, $testMask)
};

declare function geox:contains($geom1 , $geom2 ) as xs:boolean {
    java:contains($geom1, $geom2)
};

declare function geox:contains($arg1 , $arg2 , $matchAll as xs:boolean) as xs:boolean {
    java:contains($arg1, $arg2, $matchAll)
};

declare function geox:containsGeomGeom($geom1 , $geom2 ) as xs:boolean {
    java:containsGeomGeom($geom1, $geom2)
};

declare function geox:containsGeomGeom($geom1 , $geom2 , $matchAll as xs:boolean) as xs:boolean {
    java:containsGeomGeom($geom1, $geom2, $matchAll)
};

declare function geox:crosses($geom1 , $geom2 ) as xs:boolean {
    java:crosses($geom1, $geom2)
};

declare function geox:crosses($arg1 , $arg2 , $matchAll as xs:boolean) as xs:boolean {
    java:crosses($arg1, $arg2, $matchAll)
};

declare function geox:crossesGeomGeom($geom1 , $geom2 ) as xs:boolean {
    java:crossesGeomGeom($geom1, $geom2)
};

declare function geox:crossesGeomGeom($geom1 , $geom2 , $matchAll as xs:boolean) as xs:boolean {
    java:crossesGeomGeom($geom1, $geom2, $matchAll)
};

declare function geox:equals($geom1 , $geom2 ) as xs:boolean {
    java:equals($geom1, $geom2)
};

declare function geox:equals($arg1 , $arg2 , $matchAll as xs:boolean) as xs:boolean {
    java:equals($arg1, $arg2, $matchAll)
};

declare function geox:equalsGeomGeom($geom1 , $geom2 ) as xs:boolean {
    java:equalsGeomGeom($geom1, $geom2)
};

declare function geox:equalsGeomGeom($geom1 , $geom2 , $matchAll as xs:boolean) as xs:boolean {
    java:equalsGeomGeom($geom1, $geom2, $matchAll)
};

declare function geox:intersects($geom1 , $geom2 ) as xs:boolean {
    java:intersects($geom1, $geom2)
};

declare function geox:intersects($arg1 , $arg2 , $matchAll as xs:boolean) as xs:boolean {
    java:intersects($arg1, $arg2, $matchAll)
};

declare function geox:intersectsGeomGeom($geom1 , $geom2 ) as xs:boolean {
    java:intersectsGeomGeom($geom1, $geom2)
};

declare function geox:intersectsGeomGeom($geom1 , $geom2 , $matchAll as xs:boolean) as xs:boolean {
    java:intersectsGeomGeom($geom1, $geom2, $matchAll)
};

declare function geox:determineSrsName($geometryNode ) as xs:string {
    java:determineSrsName($geometryNode)
};

declare function geox:determineSrsNameForGeometryComponent($geometryComponentNode ) as xs:string {
    java:determineSrsNameForGeometryComponent($geometryComponentNode)
};

declare function geox:parseGeometry($v ) {
    java:parseGeometry($v)
};

declare function geox:isDisjoint($geom1 , $geom2 ) as xs:boolean {
    java:isDisjoint($geom1, $geom2)
};

declare function geox:isDisjoint($arg1 , $arg2 , $matchAll as xs:boolean) as xs:boolean {
    java:isDisjoint($arg1, $arg2, $matchAll)
};

declare function geox:isDisjointGeomGeom($geom1 , $geom2 ) as xs:boolean {
    java:isDisjointGeomGeom($geom1, $geom2)
};

declare function geox:isDisjointGeomGeom($geom1 , $geom2 , $matchAll as xs:boolean) as xs:boolean {
    java:isDisjointGeomGeom($geom1, $geom2, $matchAll)
};

declare function geox:isWithin($geom1 , $geom2 ) as xs:boolean {
    java:isWithin($geom1, $geom2)
};

declare function geox:isWithin($arg1 , $arg2 , $matchAll as xs:boolean) as xs:boolean {
    java:isWithin($arg1, $arg2, $matchAll)
};

declare function geox:isWithinGeomGeom($geom1 , $geom2 ) as xs:boolean {
    java:isWithinGeomGeom($geom1, $geom2)
};

declare function geox:isWithinGeomGeom($geom1 , $geom2 , $matchAll as xs:boolean) as xs:boolean {
    java:isWithinGeomGeom($geom1, $geom2, $matchAll)
};

declare function geox:overlaps($geom1 , $geom2 ) as xs:boolean {
    java:overlaps($geom1, $geom2)
};

declare function geox:overlaps($arg1 , $arg2 , $matchAll as xs:boolean) as xs:boolean {
    java:overlaps($arg1, $arg2, $matchAll)
};

declare function geox:overlapsGeomGeom($geom1 , $geom2 ) as xs:boolean {
    java:overlapsGeomGeom($geom1, $geom2)
};




declare function geox:overlapsGeomGeom($geom1 , $geom2 , $matchAll as xs:boolean) as xs:boolean {
    java:overlapsGeomGeom($geom1, $geom2, $matchAll)
};




declare function geox:touches($geom1 , $geom2 ) as xs:boolean {
    java:touches($geom1, $geom2)
};




declare function geox:touches($arg1 , $arg2 , $matchAll as xs:boolean) as xs:boolean {
    java:touches($arg1, $arg2, $matchAll)
};




declare function geox:touchesGeomGeom($geom1 , $geom2 ) as xs:boolean {
    java:touchesGeomGeom($geom1, $geom2)
};




declare function geox:touchesGeomGeom($geom1 , $geom2 , $matchAll as xs:boolean) as xs:boolean {
    java:touchesGeomGeom($geom1, $geom2, $matchAll)
};



declare function geox:registerGmlGeometry($gmlGeometry as xs:string) as empty-sequence() {
    java:registerGmlGeometry($gmlGeometry)
};



declare function geox:setStandardSRS($srsName as xs:string) as empty-sequence() {
    java:setStandardSRS($srsName)
};



declare function geox:setMaxNumPointsForInterpolation($maxNumPoints as xs:int) as empty-sequence() {
    java:setMaxNumPointsForInterpolation($maxNumPoints)
};



declare function geox:setMaxErrorForInterpolation($maxError as xs:double) as empty-sequence() {
    java:setMaxErrorForInterpolation($maxError)
};



declare function geox:unregisterGmlGeometry($nodeName as xs:string) as empty-sequence() {
    java:unregisterGmlGeometry($nodeName)
};



declare function geox:unregisterAllGmlGeometries() as empty-sequence() {
    java:unregisterAllGmlGeometries()
};



declare function geox:registeredGmlGeometries() as xs:string {
    java:registeredGmlGeometries()
};



declare function geox:unionGeom($arg) {
    java:unionGeom($arg)
};



declare function geox:union($val) {
    java:union($val)
};



declare function geox:isEmptyGeom($geom ) as xs:boolean {
    java:isEmptyGeom($geom)
};



declare function geox:checkSecondControlPointInMiddleThirdOfArc($arcStringNode ) {
    java:checkSecondControlPointInMiddleThirdOfArc($arcStringNode)
};




declare function geox:checkMinimumSeparationOfCircleControlPoints($circleNode , $minSeparationInDegree )  {
    java:checkMinimumSeparationOfCircleControlPoints($circleNode, $minSeparationInDegree)
};



declare function geox:isClosedGeom($geom ) as xs:boolean {
    java:isClosedGeom($geom)
};



declare function geox:isClosed($geom ) as xs:boolean {
    java:isClosed($geom)
};




declare function geox:isClosedGeom($geom , $onlyCheckCurveGeometries as xs:boolean) as xs:boolean {
    java:isClosedGeom($geom, $onlyCheckCurveGeometries)
};

declare function geox:isClosed($geomNode , $onlyCheckCurveGeometries as xs:boolean) as xs:boolean {
    java:isClosed($geomNode, $onlyCheckCurveGeometries)
};

declare function geox:holes($geometryNode ) {
    java:holes($geometryNode)
};

declare function geox:holesGeom($geom ) {
    java:holesGeom($geom)
};



declare function geox:holesAsGeometryCollection($geom ) {
    java:holesAsGeometryCollection($geom)
};



declare function geox:isValid($geometryNode ) as xs:boolean {
    java:isValid($geometryNode)
};




declare function geox:relate($arg1 , $arg2 , $intersectionPattern as xs:string) as xs:boolean {
    java:relate($arg1, $arg2, $intersectionPattern)
};





declare function geox:relate($value1 , $value2 , $intersectionPattern as xs:string, $matchAll as xs:boolean) as xs:boolean {
    java:relate($value1, $value2, $intersectionPattern, $matchAll)
};




declare function geox:relateGeomGeom($geom1 , $geom2 , $intersectionPattern as xs:string) as xs:boolean {
    java:relateGeomGeom($geom1, $geom2, $intersectionPattern)
};





declare function geox:relateGeomGeom($geom1 , $geom2 , $intersectionPattern as xs:string, $matchAll as xs:boolean) as xs:boolean {
    java:relateGeomGeom($geom1, $geom2, $intersectionPattern, $matchAll)
};




declare function geox:intersection($geometry1 , $geometry2 ) {
    java:intersection($geometry1, $geometry2)
};




declare function geox:intersectionGeomGeom($geometry1 , $geometry2 ) {
    java:intersectionGeomGeom($geometry1, $geometry2)
};




declare function geox:difference($geometry1 , $geometry2 ) {
    java:difference($geometry1, $geometry2)
};



declare function geox:boundary($geometryNode ) {
    java:boundary($geometryNode)
};



declare function geox:boundaryGeom($geometry ) {
    java:boundaryGeom($geometry)
};




declare function geox:differenceGeomGeom($geometry1 , $geometry2 ) {
    java:differenceGeomGeom($geometry1, $geometry2)
};



declare function geox:envelope($geometryNode )  {
    java:envelope($geometryNode)
};



declare function geox:envelopeGeom($geometry )  {
    java:envelopeGeom($geometry)
};



declare function geox:curveEndPoints($geomNode )  {
    java:curveEndPoints($geomNode)
};





declare function geox:search($minx , $miny , $maxx , $maxy )  {
    java:search($minx, $miny, $maxx, $maxy)
};





declare function geox:search($indexName as xs:string, $minx , $miny , $maxx , $maxy )  {
    java:search($indexName, $minx, $miny, $maxx, $maxy)
};



declare function geox:search($geometryNode )  {
    java:search($geometryNode)
};




declare function geox:search($indexName as xs:string, $geometryNode )  {
    java:search($indexName, $geometryNode)
};



declare function geox:searchGeom($geom )  {
    java:searchGeom($geom)
};



declare function geox:searchGeom($indexName as xs:string, $geom )  {
    java:searchGeom($indexName, $geom)
};


declare function geox:search()  {
    java:search()
};


declare function geox:searchInIndex($indexName as xs:string)  {
    java:searchInIndex($indexName)
};


declare function geox:cacheSize($size ) as empty-sequence() {
    java:cacheSize($size)
};


declare function geox:getCacheSize() as xs:int {
    java:getCacheSize()
};


declare function geox:index($node , $geometry ) as empty-sequence() {
    java:index($node, $geometry)
};


declare function geox:removeIndex($indexName as xs:string) as empty-sequence() {
    java:removeIndex($indexName)
};


declare function geox:index($indexName as xs:string, $node , $geometry ) as empty-sequence() {
    java:index($indexName, $node, $geometry)
};


declare function geox:pointCoordInGeometryCoords($point , $geometry ) as xs:boolean {
    java:pointCoordInGeometryCoords($point, $geometry)
};


declare function geox:curveUnmatchedByIdenticalCurvesMin($geomNode , $otherGeomsNodes , $minMatchesPerCurve as xs:int) {
    java:curveUnmatchedByIdenticalCurvesMin($geomNode, $otherGeomsNodes, $minMatchesPerCurve)
};


declare function geox:curveUnmatchedByIdenticalCurvesMax($geomNode , $otherGeomsNodes , $maxMatchesPerCurve as xs:int) {
    java:curveUnmatchedByIdenticalCurvesMax($geomNode, $otherGeomsNodes, $maxMatchesPerCurve)
};


declare function geox:determineIncompleteCoverageByIdenticalCurveComponents($geomNode , $otherGeomNodes ) {
    java:determineIncompleteCoverageByIdenticalCurveComponents($geomNode, $otherGeomNodes)
};

declare function geox:determineInteriorIntersectionOfCurveComponents($geomNode1 , $geomNode2 ) {
    java:determineInteriorIntersectionOfCurveComponents($geomNode1, $geomNode2)
};

declare function geox:getOrCacheGeometry($geomNode ) {
    java:getOrCacheGeometry($geomNode)
};


declare function geox:prepareSpatialIndex($node , $geometry ) as empty-sequence() {
    java:prepareSpatialIndex($node, $geometry)
};


declare function geox:prepareSpatialIndex($indexName as xs:string, $node , $geometry ) as empty-sequence() {
    java:prepareSpatialIndex($indexName, $node, $geometry)
};


declare function geox:prepareDefaultAndSpecificSpatialIndex($indexName as xs:string, $node , $geometry ) as empty-sequence() {
    java:prepareDefaultAndSpecificSpatialIndex($indexName, $node, $geometry)
};


declare function geox:buildSpatialIndex() as empty-sequence() {
    java:buildSpatialIndex()
};

declare function geox:buildSpatialIndex($indexName as xs:string) as empty-sequence() {
    java:buildSpatialIndex($indexName)
};

declare function geox:georefFromGeom($geom ) as xs:string* {
    java:georefFromGeom($geom)
};

declare function geox:georefFromCoord($coord ) as xs:string* {
    java:georefFromCoord($coord)
};
declare function geox:detailedVersion() as xs:string {
    java:detailedVersion()
};
declare function geox:getModuleInstance() {
    java:getModuleInstance()
};
