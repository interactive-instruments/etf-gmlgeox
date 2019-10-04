import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

let $members := db:open("GmlGeoXUnitTestDB-000")//ii:member

let $init := ggeo:init('GmlGeoXUnitTestDB-000')
let $geom1 := ggeo:getOrCacheGeometry($members[@gml:id = 'Member_1']/*)
let $geom2 := ggeo:getOrCacheGeometry($members[@gml:id = 'Member_2']/*)
let $result := ggeo:equalsGeomGeom($geom1,$geom2)

return
 <test_arc_interpolation_compositeSurface>  
  <relationshiptest>
    <tests>
     <test>{$result}</test>     
     </tests>
  </relationshiptest>
 </test_arc_interpolation_compositeSurface>
