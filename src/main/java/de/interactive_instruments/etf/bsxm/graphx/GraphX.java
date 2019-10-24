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

import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.value.node.DBNode;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

/**
 * This module supports the creation of graphs and performing algorithms on them.
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
final public class GraphX extends QueryModule {

    private SimpleGraph<DBNode, DefaultEdge> simpleGraph = new SimpleGraph<>(DefaultEdge.class);

    public GraphX() {}

    @Requires(Permission.NONE)
    public void resetSimpleGraph() {
        simpleGraph = new SimpleGraph<>(DefaultEdge.class);
    }

    @Requires(Permission.NONE)
    public void addVertexToSimpleGraph(DBNode vertex) throws QueryException {
        simpleGraph.addVertex(vertex);
    }

    @Requires(Permission.NONE)
    public void addEdgeToSimpleGraph(DBNode vertex1, DBNode vertex2) throws QueryException {
        simpleGraph.addEdge(vertex1, vertex2);
    }

    /**
     * @return list with connected sets (represented as a list of nodes of the vertexes in the set) found in the simple graph; can be empty (if the graph is empty) but not <code>null</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public List<List<DBNode>> determineConnectedSetsInSimpleGraph() throws QueryException {

        final ConnectivityInspector<DBNode, DefaultEdge> ci = new ConnectivityInspector<>(simpleGraph);

        final List<Set<DBNode>> connectedSets = ci.connectedSets();

        final List<List<DBNode>> result = new ArrayList<>();

        for (final Set<DBNode> s : connectedSets) {
            result.add(new ArrayList<DBNode>(s));
        }

        return result;
    }

}
