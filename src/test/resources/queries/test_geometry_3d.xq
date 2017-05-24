import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';

declare namespace gml = 'http://www.opengis.net/gml/3.2';
declare namespace ii = 'http://www.interactive-instruments.de/test';

let $doc := <ii:GeometryCollection
 xmlns:gml="http://www.opengis.net/gml/3.2"
 xmlns:ii="http://www.interactive-instruments.de/test"
 xmlns:xlink="http://www.w3.org/1999/xlink"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
 <gml:boundedBy>
  <gml:Envelope
   srsName="urn:ogc:def:crs:epsg:4979"/>
 </gml:boundedBy>
 <ii:member>
  <gml:Point
   gml:id="Point_1">
   <gml:pos>1 1 1</gml:pos>
  </gml:Point>
 </ii:member>
 <ii:member>
  <gml:Point
   gml:id="Point_2"
   srsName="urn:ogc:def:crs:epsg:4979">
   <gml:pos>0.5 0 0</gml:pos>
  </gml:Point>
 </ii:member>
 <ii:member>
  <gml:Curve
   gml:id="Curve_1">
   <gml:segments>
    <gml:LineStringSegment
     interpolation="linear">
     <gml:posList>0 0 0 1 1 1 1 4 1 0 4 1 0 0 0</gml:posList>
    </gml:LineStringSegment>
   </gml:segments>
  </gml:Curve>
 </ii:member>
 <ii:member>
  <gml:Surface
   gml:id="Surface"
   srsName="urn:ogc:def:crs:epsg:4979">
   <gml:patches>
    <gml:PolygonPatch
     interpolation="planar">
     <gml:exterior>
      <gml:Ring>
       <gml:curveMember>
        <gml:Curve
         gml:id="Curve_S1">
         <gml:segments>
          <gml:LineStringSegment
           interpolation="linear">
           <gml:posList>0 0 0 1 0 0 2 0 0</gml:posList>
          </gml:LineStringSegment>
         </gml:segments>
        </gml:Curve>
       </gml:curveMember>
       <gml:curveMember>
        <gml:Curve
         gml:id="Curve_S2">
         <gml:segments>
          <gml:LineStringSegment
           interpolation="linear">
           <gml:posList>2 0 0 2 1 0 2 2 0</gml:posList>
          </gml:LineStringSegment>
         </gml:segments>
        </gml:Curve>
       </gml:curveMember>
       <gml:curveMember>
        <gml:Curve
         gml:id="Curve_S3">
         <gml:segments>
          <gml:LineStringSegment
           interpolation="linear">
           <gml:posList>2 2 0 1 2 0 0 2 0</gml:posList>
          </gml:LineStringSegment>
         </gml:segments>
        </gml:Curve>
       </gml:curveMember>
       <gml:curveMember>
        <gml:Curve
         gml:id="Curve_S4">
         <gml:segments>
          <gml:LineStringSegment
           interpolation="linear">
           <gml:posList>0 2 0 0 1 0 0 0 0</gml:posList>
          </gml:LineStringSegment>
         </gml:segments>
        </gml:Curve>
       </gml:curveMember>
      </gml:Ring>
     </gml:exterior>
    </gml:PolygonPatch>
   </gml:patches>
  </gml:Surface>
 </ii:member>
</ii:GeometryCollection>

let $geometries := $doc//ii:member/*
return
 <test_3d>
  <validationtest>
   {
    for $geom in $geometries
    let $vr := ggeo:validateAndReport($geom)
    return
     <test
      id='{$geom/@gml:id}'>
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
  <relationshiptest>
   {
    let $geom1 := $geometries[@gml:id = 'Point_1']
    let $geom2 := $geometries[@gml:id = 'Curve_1']
    let $ggeogeom1 := ggeo:parseGeometry($geom1)
    let $ggeogeom2 := ggeo:parseGeometry($geom2)
    return
     <test
      geom1='{$geom1/@gml:id}'
      geom2='{$geom2/@gml:id}'>
      <intersects>
       {ggeo:intersects($ggeogeom1, $ggeogeom2)}
      </intersects>
     </test>
   }
  </relationshiptest>
  <relationshiptest>
   {
    let $geom1 := $geometries[@gml:id = 'Point_2']
    let $geom2 := $geometries//*[@gml:id = 'Curve_S1']
    let $ggeogeom1 := ggeo:parseGeometry($geom1)
    let $ggeogeom2 := ggeo:parseGeometry($geom2)
    return
     <test
      geom1='{$geom1/@gml:id}'
      geom2='{$geom2/@gml:id}'>
      <intersects>
       {ggeo:intersects($ggeogeom1, $ggeogeom2)}
      </intersects>
     </test>
   }
  </relationshiptest>
 </test_3d>