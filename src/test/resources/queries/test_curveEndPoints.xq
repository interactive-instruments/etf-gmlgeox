import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

let $members := db:open("GmlGeoXUnitTestDB-000")//ii:member
let $init := ggeo:init('GmlGeoXUnitTestDB-000')

let $geometries := $members/*
return
 <test_curveEndPoints>    
   {
    for $geom in $geometries
    order by $geom/@gml:id
    let $endPoints := ggeo:curveEndPoints($geom)    
    let $result := string-join(($endPoints ! ggeo:toWKT(.)),', ')
    return 
      <test geom='{$geom/@gml:id}'>{$result}</test>    
   }
 </test_curveEndPoints>
