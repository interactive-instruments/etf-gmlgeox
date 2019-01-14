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
package de.interactive_instruments.etf.bsxm;

import org.basex.query.value.node.DBNode;

/**
 * This class contains BaseX information to quickly access a node in the database. The instances are for example stored in the spatial index.
 *
 * @author Clemens Portele (portele <at> interactive-instruments <dot> de)
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments <dot> de)
 */
class DBNodeEntry {

    final int pre;
    final String dbname;
    final int nodeKind;

    /**
     * Create entry from database node
     *
     * @param node
     *            Database node
     */
    DBNodeEntry(final DBNode node) {
        pre = node.pre();
        dbname = node.data().meta.name;
        nodeKind = node.kind();
    }
}
