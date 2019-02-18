import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

let $members := db:open("GmlGeoXUnitTestDB")//ii:member

let $geometries := $members/*[.//*[self::gml:Arc or self::gml:ArcString]]
return
 <test_checkSecondControlPointInMiddleThirdOfArc>    
   {
    for $geom in $geometries
    let $arcStrings := $geom//*[self::gml:Arc or self::gml:ArcString]
    let $invalidCase :=
     (for $arcString in $arcStrings
      return
        ggeo:checkSecondControlPointInMiddleThirdOfArc($arcString)
      )[position() le 1]
    let $result :=
      if($invalidCase) then
        'failed'
      else
        'passed'
    return 
      <test geom='{$geom/@gml:id}'>{$result}</test>    
   }
 </test_checkSecondControlPointInMiddleThirdOfArc>
