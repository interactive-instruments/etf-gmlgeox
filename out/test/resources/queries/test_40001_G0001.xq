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

import module namespace functx = 'http://www.functx.com';
import module namespace http = 'http://expath.org/ns/http-client';
import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';


(:
============
 VARIABLES
============
:)


declare variable $files_to_test external := "BY_XtraServer_2015-11-16_[0-2][0-9][0-9]von710_.*.xml";
declare variable $Modellart external := "Basis-DLM";
declare variable $limitErrors external := 1000;

declare variable $dbname := 'etf-test-0';


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


let $db := db:open($dbname)[matches(db:path(.), $files_to_test)]

let $features := $db/wfs:FeatureCollection/wfs:member/* | $db/gml:FeatureCollection/gml:featureMember/* | $db/gml:FeatureCollection/gml:featureMembers/* | $db/base:SpatialDataSet/base:member/* | $db/base32:SpatialDataSet/base32:member/* | $db/adv:AX_NutzerbezogeneBestandsdatenaktualisierung_NBA/adv:geaenderteObjekte/wfsadv:Transaction/*/*[adv:modellart/adv:AA_Modellart[matches(adv:advStandardModell, $Modellart)]]


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

let $tn := $features[local-name() = ('AX_Fliessgewaesser', 'AX_Hafenbecken', 'AX_Meer', 'AX_StehendesGewaesser', 'AX_Bergbaubetrieb', 'AX_FlaecheBesondererFunktionalerPraegung', 'AX_FlaecheGemischterNutzung', 'AX_Friedhof', 'AX_Halde', 'AX_IndustrieUndGewerbeflaeche', 'AX_Siedlungsflaeche', 'AX_SportFreizeitUndErholungsflaeche', 'AX_TagebauGrubeSteinbruch', 'AX_Wohnbauflaeche', 'AX_FlaecheZurZeitUnbestimmbar', 'AX_Gehoelz', 'AX_Heide', 'AX_Landwirtschaft', 'AX_Moor', 'AX_Sumpf', 'AX_UnlandVegetationsloseFlaeche', 'AX_Wald', 'AX_Bahnverkehr', 'AX_Flugverkehr', 'AX_Platz', 'AX_Schiffsverkehr', 'AX_Strassenverkehr', 'AX_Weg') and not(adv:hatDirektUnten)]

let $startUnion := prof:current-ms()
let $tnunion := ggeo:unionNodes($tn/adv:position/*[1])
let $durationUnion := prof:current-ms() - $startUnion
let $logentryUnion := local:log('Creating the union: ' || $durationUnion || ' ms')

let $holes := ggeo:holes($tnunion)

let $startErrors := prof:current-ms()

let $featuresWithErrors :=
(for $candidate in $tn
let $candidate_geometryNode := $candidate/adv:position/*[1]
let $candidate_geometry := ggeo:getOrCacheGeometry($candidate_geometryNode)
let $other_features := ggeo:search($candidate_geometryNode)
let $other_geometryNodes := for $feature in $other_features[local-name() = ('AX_Fliessgewaesser', 'AX_Hafenbecken', 'AX_Meer', 'AX_StehendesGewaesser', 'AX_Bergbaubetrieb', 'AX_FlaecheBesondererFunktionalerPraegung', 'AX_FlaecheGemischterNutzung', 'AX_Friedhof', 'AX_Halde', 'AX_IndustrieUndGewerbeflaeche', 'AX_Siedlungsflaeche', 'AX_SportFreizeitUndErholungsflaeche', 'AX_TagebauGrubeSteinbruch', 'AX_Wohnbauflaeche', 'AX_FlaecheZurZeitUnbestimmbar', 'AX_Gehoelz', 'AX_Heide', 'AX_Landwirtschaft', 'AX_Moor', 'AX_Sumpf', 'AX_UnlandVegetationsloseFlaeche', 'AX_Wald', 'AX_Bahnverkehr', 'AX_Flugverkehr', 'AX_Platz', 'AX_Schiffsverkehr', 'AX_Strassenverkehr', 'AX_Weg') and not(adv:hatDirektUnten)]
return
 $feature/adv:position/*[1]
let $testOverlap := ggeo:overlaps($candidate_geometryNode, $other_geometryNodes, false())
let $testIntersectsHoles := ggeo:intersectsGeomGeom($candidate_geometry, $holes, false())
return
 if ($testOverlap or $testIntersectsHoles) then
  $candidate
 else
  ())[position() le $limitErrors]

let $durationErrors := prof:current-ms() - $startErrors
let $logentryErrors := local:log('Computing features with errors: ' || $durationErrors || ' ms')

let $duration := prof:current-ms() - $start
let $logTotalTime := local:log('Total time: ' || $duration || ' ms')

return
 <result
  status="{
    if ($featuresWithErrors) then
     'FAILED'
    else
     'PASSED'
   }">
  {
   (:local:error-statistics('TR.featuresWithErrors', count($featuresWithErrors)),
     for $feature in
     $featuresWithErrors
      order by $feature/@gml:id
     return
      local:addMessage('TR.bdlmerr.1', map {'filename': local:filename($feature)}):)
   <featuresWithErrors>
    <count>{count($featuresWithErrors)}</count>
    {
     for $feature in $featuresWithErrors
     let $filename := local:filename($feature)
      group by $filename
      order by $filename
     return
      <file
       name="{$filename}">{
        for $f in $feature
        let $gmlid := $f/@gml:id
         order by $gmlid
        return
         <feature
          gmlid="{$gmlid}"/>
       }
      </file>
    }
   </featuresWithErrors>
  }
 </result>