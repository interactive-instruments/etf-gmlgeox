/**
 * Copyright 2010-2019 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.bsxm.graphx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.basex.query.QueryModule;
import org.basex.query.value.node.DBNode;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import de.interactive_instruments.etf.bsxm.node.DBNodeRef;
import de.interactive_instruments.etf.bsxm.node.DBNodeRefFactory;
import de.interactive_instruments.etf.bsxm.node.DBNodeRefLookup;

/**
 * This module supports the creation of graphs and performing algorithms on them.
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
final public class GraphX extends QueryModule {

    private SimpleGraph<DBNodeRef, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);

    private DBNodeRefLookup dbNodeRefLookup;
    private DBNodeRefFactory dbNodeRefFactory;

    public GraphX() {}

    @Requires(Permission.NONE)
    public void resetSimpleGraph() {
        simpleGraph = new SimpleGraph<>(DefaultEdge.class);
    }

    @Requires(Permission.NONE)
    public void addVertexToSimpleGraph(DBNode vertex) {
        final DBNodeRef nodeEntry = this.dbNodeRefFactory.createDBNodeEntry(vertex);
        simpleGraph.addVertex(nodeEntry);
    }

    @Requires(Permission.NONE)
    public void addEdgeToSimpleGraph(DBNode vertex1, DBNode vertex2) {
        final DBNodeRef nodeEntry1 = this.dbNodeRefFactory.createDBNodeEntry(vertex1);
        final DBNodeRef nodeEntry2 = this.dbNodeRefFactory.createDBNodeEntry(vertex2);
        simpleGraph.addEdge(nodeEntry1, nodeEntry2);
    }

    @Requires(Permission.NONE)
    public void init(final String databaseName) {
        this.dbNodeRefFactory = DBNodeRefFactory.create(databaseName);
        this.dbNodeRefLookup = new DBNodeRefLookup(this.queryContext, this.dbNodeRefFactory);
    }

    /**
     * @return list with connected sets (represented as a list of nodes of the vertexes in the set) found in the simple graph; can be empty (if the graph is empty) but not <code>null</code>
     */
    @Requires(Permission.NONE)
    public List<List<DBNode>> determineConnectedSetsInSimpleGraph() {
        final ConnectivityInspector<DBNodeRef, DefaultEdge> ci = new ConnectivityInspector<>(simpleGraph);
        final List<Set<DBNodeRef>> connectedSets = ci.connectedSets();
        final List<List<DBNode>> result = new ArrayList<>(connectedSets.size());
        for (final Set<DBNodeRef> s : connectedSets) {
            final List<DBNode> nodeList = new ArrayList<>(s.size());
            for (DBNodeRef nodeRef : s) {
                nodeList.add(this.dbNodeRefLookup.resolve(nodeRef));
            }
            result.add(nodeList);
        }
        return result;
    }

}
