import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';
import module namespace modulestore = 'de.interactive_instruments.etf.bsxm.GmlGeoXStoreTester';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

declare variable $restoreModules external := 'false';

let $members := db:open("GmlGeoXUnitTestDB")//ii:member

let $dummyRestoreModules :=
  if($restoreModules = 'true') then
    let $gmlgeoxModule := ggeo:getModuleInstance()
    let $dummyRestoreGmlGeoXModule := modulestore:restoreQueryModule($gmlgeoxModule,'GmlGeoX')
    return
      prof:dump('Restored GmlGeoX module.')
  else
    prof:dump('Using modules as-is.')

(: Test retrieval of pre-computed envelope for geometry node. :)
let $env1 := ggeo:envelope($members[@gml:id = 'Mem.1']/*)
let $result1 := string-join($env1,' ')

(: Test search via spatial index. :)
let $spatialIndexSearchResults := ggeo:search($members[@gml:id = 'Mem.2']/*)
let $result2 := string-join(
 (for $res in $spatialIndexSearchResults
  order by $res/@gml:id
  return
    $res/@gml:id
 ),' ')
  
return
 <test_moduleStorage>  
  <test1_envelope>{$result1}</test1_envelope>  
  <test2_search>{$result2}</test2_search>
 </test_moduleStorage>