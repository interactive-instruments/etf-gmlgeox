import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

let $members := db:open("GmlGeoXUnitTestDB-000")//ii:member
let $init := ggeo:init('GmlGeoXUnitTestDB-000')

let $geometries := $members/*
return
 <test_determineInteriorIntersectionOfCurveComponents>
  <test
   geom1="c1"
   geom2="c3">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'c1'], $geometries[@gml:id = 'c3']))}</test>
  <test
   geom1="c1"
   geom2="c2">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'c1'], $geometries[@gml:id = 'c2']))}</test>
  <test
   geom1="c2"
   geom2="c3">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'c2'], $geometries[@gml:id = 'c3']))}</test>
  <test
   geom1="c4"
   geom2="p1">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'c4'], $geometries[@gml:id = 'p1']))}</test>
     <test
   geom1="p2"
   geom2="p2">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'p2'], $geometries[@gml:id = 'p2']))}</test>
  <test
   geom1="p2"
   geom2="c4">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'p2'], $geometries[@gml:id = 'c4']))}</test>
  <test
   geom1="c5"
   geom2="c6">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'c5'], $geometries[@gml:id = 'c6']))}</test>
  <test
   geom1="c7"
   geom2="c8">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'c7'], $geometries[@gml:id = 'c8']))}</test>
  <test
   geom1="c9"
   geom2="c10">{exists(ggeo:determineInteriorIntersectionOfCurveComponents($geometries[@gml:id = 'c9'], $geometries[@gml:id = 'c10']))}</test>
 </test_determineInteriorIntersectionOfCurveComponents>
