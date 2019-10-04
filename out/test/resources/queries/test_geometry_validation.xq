import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

declare variable $docPath external := 'C:/REPOSITORIES/ii/extern/GitHub/interactive_instruments/etf-gmlgeox/src/test/resources/xml/geometryRelationship/GeometryValidationTest.xml';

let $init := ggeo:init('GmlGeoXUnitTestDB-000')

let $geometries := db:open("GmlGeoXUnitTestDB-000")//ii:member/*
return
 <validationtest>
  {
   for $geom in $geometries
   let $vr := ggeo:validateAndReport($geom)
   return
    <test>
     <valid>{
       if (xs:boolean($vr/ggeo:valid)) then
        'true'
       else
        'false'
      }</valid>
     <result>{
       $vr/ggeo:result/text()
      }</result>
     <messages>
      {
       for $error in $vr/ggeo:error
       return
        <error>
         <msg>{
           $error/ggeo:msg/text()
          }</msg>
         <id>{
           $error/ggeo:loc/text()
          }</id>
         <context>{
           data($error/ggeo:loc/@context)
          }</context>
         <coordinates>{
           $error/ggeo:coordinates/text()
          }</coordinates>
        </error>
      }
     </messages>
    </test>
  }
 </validationtest>
