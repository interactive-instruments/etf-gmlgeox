declare namespace csw = 'http://www.opengis.net/cat/csw/2.0.2';
declare namespace gsr = 'http://www.isotc211.org/2005/gsr';
declare namespace gss = 'http://www.isotc211.org/2005/gss';
declare namespace gts = 'http://www.isotc211.org/2005/gts';
declare namespace gmx = 'http://www.isotc211.org/2005/gmx';
declare namespace srv = 'http://www.isotc211.org/2005/srv';
declare namespace gco = 'http://www.isotc211.org/2005/gco';
declare namespace gmd = 'http://www.isotc211.org/2005/gmd';
declare namespace adv = 'http://www.adv-online.de/namespaces/adv/gid/6.0';
declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace gml31 = 'http://www.opengis.net/gml';
declare namespace xsi = 'http://www.w3.org/2001/XMLSchema-instance';
declare namespace xlink = 'http://www.w3.org/1999/xlink';
declare namespace etf = 'http://www.interactive-instruments.de/etf/2.0';
declare namespace atom = 'http://www.w3.org/2005/Atom';
declare namespace wfs = 'http://www.opengis.net/wfs/2.0';
declare namespace wfsadv = 'http://www.adv-online.de/namespaces/adv/gid/wfs';
declare namespace wcs = 'http://www.opengis.net/wcs/2.0';
declare namespace sos = 'http://www.opengis.net/sos/2.0';
declare namespace wms = 'http://www.opengis.net/wms';

declare namespace au3 = 'urn:x-inspire:specification:gmlas:AdministrativeUnits:3.0';
declare namespace ad3 = 'urn:x-inspire:specification:gmlas:Addresses:3.0';
declare namespace cp3 = 'urn:x-inspire:specification:gmlas:CadastralParcels:3.0';
declare namespace gn3 = 'urn:x-inspire:specification:gmlas:GeographicalNames:3.0';
declare namespace hy3 = 'urn:x-inspire:specification:gmlas:HydroBase:3.0';
declare namespace hy-n3 = 'urn:x-inspire:specification:gmlas:HydroNetwork:3.0';
declare namespace hy-p3 = 'urn:x-inspire:specification:gmlas:HydroPhysicalWaters:3.0';
declare namespace ps3 = 'urn:x-inspire:specification:gmlas:ProtectedSites:3.0';
declare namespace tn3 = 'urn:x-inspire:specification:gmlas:CommonTransportElements:3.0';
declare namespace tn-a3 = 'urn:x-inspire:specification:gmlas:AirTransportNetwork:3.0';
declare namespace tn-c3 = 'urn:x-inspire:specification:gmlas:CableTransportNetwork:3.0';
declare namespace tn-ra3 = 'urn:x-inspire:specification:gmlas:RailwayTransportNetwork:3.0';
declare namespace tn-ro3 = 'urn:x-inspire:specification:gmlas:RoadTransportNetwork:3.0';
declare namespace tn-w3 = 'urn:x-inspire:specification:gmlas:WaterTransportNetwork:3.0';
declare namespace bu-core3d3 = 'http://inspire.jrc.ec.europa.eu/draft-schemas/bu-core3d/3.0';
declare namespace net3 = 'urn:x-inspire:specification:gmlas:Network:3.2';
declare namespace base32 = 'urn:x-inspire:specification:gmlas:BaseTypes:3.2';
declare namespace hy = 'http://inspire.ec.europa.eu/schemas/hy/4.0';
declare namespace hy-n = 'http://inspire.ec.europa.eu/schemas/hy-n/4.0';
declare namespace hy-p = 'http://inspire.ec.europa.eu/schemas/hy-p/4.0';
declare namespace au = 'http://inspire.ec.europa.eu/schemas/au/4.0';
declare namespace ad = 'http://inspire.ec.europa.eu/schemas/ad/4.0';
declare namespace cp = 'http://inspire.ec.europa.eu/schemas/cp/4.0';
declare namespace gn = 'http://inspire.ec.europa.eu/schemas/gn/4.0';
declare namespace mu = 'http://inspire.ec.europa.eu/schemas/mu/3.0';
declare namespace ps = 'http://inspire.ec.europa.eu/schemas/ps/4.0';
declare namespace tn = 'http://inspire.ec.europa.eu/schemas/tn/4.0';
declare namespace tn-a = 'http://inspire.ec.europa.eu/schemas/tn-a/4.0';
declare namespace tn-c = 'http://inspire.ec.europa.eu/schemas/tn-c/4.0';
declare namespace tn-ra = 'http://inspire.ec.europa.eu/schemas/tn-ra/4.0';
declare namespace tn-ro = 'http://inspire.ec.europa.eu/schemas/tn-ro/4.0';
declare namespace tn-w = 'http://inspire.ec.europa.eu/schemas/tn-w/4.0';
declare namespace bu-core3d = 'http://inspire.ec.europa.eu/schemas/bu-core3d/4.0';
declare namespace net = 'http://inspire.ec.europa.eu/schemas/net/4.0';
declare namespace base = 'http://inspire.ec.europa.eu/schemas/base/3.3';
declare namespace uuid = 'java.util.UUID';
import module namespace http = 'http://expath.org/ns/http-client';
import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';


(:
============
 VARIABLES
============
:)


declare variable $files_to_test external := ".*.xml";
declare variable $Modellart external := "DLKM";
declare variable $limitErrors external := 1000;

declare variable $dbname := 'GmlGeoXUnitTestDB';


(:
============
 FUNCTIONS
============
:)


declare function local:addMessage($templateId as xs:string, $map as map(*)) as element()
{
 <message
  xmlns='http://www.interactive-instruments.de/etf/2.0'
  ref='{$templateId}'>
  <translationArguments>
   {
    for $key in map:keys($map)
    return
     <argument
      token='{$key}'>{map:get($map, $key)}</argument>
   }
  </translationArguments>
 </message>
};

declare function local:error-statistics($template as xs:string, $count as xs:integer) as element()*
{
 (if ($count >= $limitErrors) then
  local:addMessage('TR.tooManyErrors', map {'count': string($limitErrors)})
 else
  (),
 if ($count > 0) then
  local:addMessage($template, map {'count': string($count)})
 else
  ())
};

declare function local:filename($element as node()) as xs:string
{
 db:path($element)
};

declare function local:log($text as xs:string) as empty-sequence()
{
 (:let $dummy := file:append($logFile, $text || file:line-separator(), map {
  "method": "text",
  "media-type": "text/plain"
 })
 return:)
 prof:dump($text)
};


(:
============
 QUERY
============
:)


let $db := db:open($dbname)

let $features := $db//gml:featureMember/*


(: Start logging :)
let $logentry := local:log('Testing ' || count($features) || ' features')

let $start := prof:current-ms()

(: Index geometries :)
let $startIndex := prof:current-ms()
let $geometryParsingErrors :=
map:merge(for $feature in $features
let $geom := ($feature//*[self::gml:Point or self::gml:LineString or self::gml:Curve or self::gml:Polygon or self::gml:PolyhedralSurface or self::gml:Surface or self::gml:MultiPoint or self::gml:MultiCurve or self::gml:MultiLineString or self::gml:MultiSurface or self::gml:MultiPolygon or self::gml:MultiGeometry])[1]
return
 if ($geom) then
  try {
   prof:void(ggeo:prepareSpatialIndex($feature, $geom))
  }
  catch * {
   map:entry($feature/@gml:id, $err:description)
  }
 else
  ())
let $xBuildSpatialIndex := prof:void(ggeo:buildSpatialIndex())
let $durationIndex := prof:current-ms() - $startIndex
let $logentryIndex := local:log('Indexing features (parsing errors: ' || map:size($geometryParsingErrors) || '): ' || $durationIndex || ' ms')

(:
Test: GKB.TatsaechlicheNutzung (40001.G0001)
Beschreibung: Lückenlose und überschneidungsfreie Flächendeckung der Objekte der Objektart Tatsächliche Nutzung.
:)


let $candidates := $features[local-name() = 'AX_Boeschungsflaeche']
return
if (count($candidates) = 0) then
  'NOT_APPLICABLE'
else
let $featuresWithErrors_mapEntries :=
 (for $candidate in $candidates
  let $c_boundary := ggeo:boundary($candidate/*:position/*)
  let $logId := local:log('candidate: ' || $candidate/@gml:id)
  let $log1 := local:log('boundary: ' || ggeo:toWKT($c_boundary))
  let $gelaendekanten := ggeo:search('AX_Gelaendekante',$candidate/*:position/*)[ggeo:intersects(./*:position/*,$candidate/*:position/*)]
  let $logGks :=
    for $gk in $gelaendekanten
    let $gkGeom := ggeo:getOrCacheGeometry($gk/*:position/*)
    return
      prof:void(local:log('gelaendekante: ' || $gk/@gml:id || ' - ' || ggeo:toWKT($gkGeom)))
  return
  if(count($gelaendekanten) lt 2) then
    map:entry(string($candidate/@gml:id), (local-name($candidate),$c_boundary))
  else
  let $gkUnion := ggeo:union($gelaendekanten/*:position/*)
  let $logUnion := local:log('union: ' || ggeo:toWKT($gkUnion))
  return
    if(ggeo:relateGeomGeom($c_boundary,$gkUnion,'**F**F***',false())) then
      ()
    else
      let $diff := ggeo:differenceGeomGeom($gkUnion,$c_boundary)
      let $logDiff := local:log('diff: ' || ggeo:toWKT($diff))
      return
        map:entry(string($candidate/@gml:id), (local-name($candidate),$diff))
 )[position() le $limitErrors]
let $featuresWithErrors_map := map:merge($featuresWithErrors_mapEntries)
return
  (if (map:size($featuresWithErrors_map) = 0) then
    'PASSED'
  else
    'FAILED',
  local:error-statistics('TR.featuresWithErrors', map:size($featuresWithErrors_map)),
  for $key in map:keys($featuresWithErrors_map)
    let $featureLocalName := map:get($featuresWithErrors_map,$key)[1]
    let $georef := ggeo:georefFromGeom(map:get($featuresWithErrors_map, $key)[2])
    order by $key
  return
    local:addMessage('TR.AdV.DE.61002.G.b.001', map {'Objektart': $featureLocalName, 'OID': $key, 'GeoRefCoord1' : $georef[1], 'GeoRefCoord2' : $georef[2]}))
