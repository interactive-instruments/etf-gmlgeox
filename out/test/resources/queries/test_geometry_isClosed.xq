import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

declare variable $docPath external := 'C:/REPOSITORIES/ii/extern/GitHub/interactive_instruments/etf-gmlgeox/src/test/resources/xml/GeometryIsClosedTest.xml';

let $init := ggeo:init('GmlGeoXUnitTestDB-000')

let $geometries := db:open("GmlGeoXUnitTestDB-000")//ii:member/*
return
 <validationtest>
  <isClosed_forCurvesOnly>
  {
   for $geom in $geometries
    return
     <test gmlid="{$geom/@gml:id}">
      <isClosed>{
        if (xs:boolean(ggeo:isClosed($geom))) then
         'true'
        else
         'false'
       }</isClosed> 
     </test>
  }
  </isClosed_forCurvesOnly>
  <isClosed_forAllGeometryTypes>
  {
   for $geom in $geometries
    return
     <test gmlid="{$geom/@gml:id}">
      <isClosed>{
        if (xs:boolean(ggeo:isClosed($geom,false()))) then
         'true'
        else
         'false'
       }</isClosed> 
     </test>
  }
  </isClosed_forAllGeometryTypes>
 </validationtest>
