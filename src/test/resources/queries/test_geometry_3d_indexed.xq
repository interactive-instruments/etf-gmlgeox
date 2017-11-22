import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

let $members := db:open("GmlGeoXUnitTestDB")//ii:member
let $geometries := $members/*
return
 <test_3d>
  <validationtest>
   {
       for $geom in $geometries
       return
       try {
            let $vr := ggeo:validateAndReport($geom,'110')
            return
            if (xs:boolean($vr/ggeo:isValid)) then ()
            else
                for $message in $vr/ggeo:message[@type='ERROR']
                return
                $message/text()
       }catch * {
            $err:description
       }
   }
  </validationtest>
  <geoIndexTest>
   {
        let $geometryParsingErrors :=
        map:merge(for $member in $members
        let $geom := ($member//*[self::gml:Point or self::gml:LineString or self::gml:Curve or self::gml:Polygon or self::gml:PolyhedralSurface or self::gml:Surface or self::gml:MultiPoint or self::gml:MultiCurve or self::gml:MultiLineString or self::gml:MultiSurface or self::gml:MultiPolygon or self::gml:MultiGeometry])[1]
        return
        if ($geom) then
            try {
                prof:void(ggeo:index($member,$member/@gml:id,$geom))
            }catch * {
                map:entry($member/@gml:id, $err:description)
            }
        else ())

        return
        if (map:size($geometryParsingErrors)=0) then 'PASSED' else
            map:for-each($geometryParsingErrors, function($a, $b) { 'gmlid: ' || $a || ' - ' || $b })

   }
  </geoIndexTest>
 </test_3d>
