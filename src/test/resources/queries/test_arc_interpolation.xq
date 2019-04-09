import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

let $members := db:open("GmlGeoXUnitTestDB")//ii:member

let $candidate_geom := $members[@gml:id = 'BOE_DEBWLT010000r2HA']/*
let $gelaendekanten_geoms := $members[@gml:id = ('GK_DEBWLT010000r2HW','GK_DEBWLT010000r2HX')]/*

let $c_boundary := ggeo:boundary($candidate_geom)
let $gkUnion := ggeo:union($gelaendekanten_geoms)

let $test1_boundaryInUnion := ggeo:relateGeomGeom($c_boundary,$gkUnion,'**F**F***',false())

return
 <test_arc_interpolation>  
  <relationshiptest>
    <tests>
     <test1>{$test1_boundaryInUnion}</test1>  
     </tests>
  </relationshiptest>
 </test_arc_interpolation>
