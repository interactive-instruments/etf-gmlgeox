import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

declare variable $docPath external := 'C:/REPOSITORIES/ii/extern/GitHub/interactive_instruments/etf-gmlgeox/src/test/resources/xml/validation/ValidationOfGeometryWithLeftHandedCRS.xml';

let $doc := fn:doc($docPath)
let $geometries := $doc//ii:member/*
return
 <validationtest>
  {
   for $geom in $geometries
   let $vr := ggeo:validateAndReport($geom)
   return
    <test>
     <isValid>{
       if (xs:boolean($vr/ggeo:isValid)) then
        'true'
       else
        'false'
      }</isValid>
     <result>{
       $vr/ggeo:result/text()
      }</result>
     <messages>
      {
       for $msg in $vr/ggeo:message
       return
        <message>
         <type>{
           $msg/data(@type)
          }</type>
         <text>{
           $msg/text()
          }</text>
        </message>
      }
     </messages>
    </test>
  }
 </validationtest>