(:~
 :
 : ---------------------------------------
 : GmlGeoX XQuery Function Library Facade
 : ---------------------------------------
 :
 : Copyright (C) 2018-2020 interactive instruments GmbH
 :
 : Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 : the European Commission - subsequent versions of the EUPL (the "Licence");
 : You may not use this work except in compliance with the Licence.
 : You may obtain a copy of the Licence at:
 :
 : https://joinup.ec.europa.eu/software/page/eupl
 :
 : Unless required by applicable law or agreed to in writing, software
 : distributed under the Licence is distributed on an "AS IS" basis,
 : WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 : See the Licence for the specific language governing permissions and
 : limitations under the Licence.
 :
 : @author  Johannes Echterhoff (echterhoff aT interactive-instruments doT de)
 :)
module namespace graph = 'https://modules.etf-validator.net/graphx/1';

import module namespace java = 'java:de.interactive_instruments.etf.bsxm.GraphX';

(:~
 : Initialize the GrapX module with the database name
 :
 : Must be run before using other functions.
 : Database names must be suffixed with a three digits index, i.e. DB-000.
 :
 : Throws BaseXException if database name is not suffixed with a three digits index.
 :
 : @param  $dbName full database name ( i.e. DB-000 )
 : @param  $dbCount number of databases
 : @return full database name $dbName
 :)
declare function graph:init($databaseName as xs:string) as empty-sequence() {
    java:init($databaseName)
};

declare function graph:resetSimpleGraph() as empty-sequence() {
    java:resetSimpleGraph()
};

declare function graph:addVertexToSimpleGraph($vertex) as empty-sequence() {
    java:addVertexToSimpleGraph($vertex)
};

declare function graph:addEdgeToSimpleGraph($vertex1, $vertex2) as empty-sequence() {
    java:addEdgeToSimpleGraph($vertex1, $vertex2)
};

declare function graph:determineConnectedSetsInSimpleGraph() {
    java:determineConnectedSetsInSimpleGraph()
};
