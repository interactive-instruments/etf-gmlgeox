import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';
declare namespace etf = 'http://www.interactive-instruments.de/etf/2.0';

declare variable $docPath external := '';

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
        for $message in $vr/ggeo:errors/*:message
        return $message
      }
     </messages>
    </test>
  }
 </validationtest>
