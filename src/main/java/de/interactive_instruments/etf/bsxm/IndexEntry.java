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

import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;

/**
 * This class contains BaseX information to quickly access a node in the database. The instances are stored in the spatial index.
 *
 * @author Clemens Portele (portele <at> interactive-instruments <dot> de)
 *
 */
class IndexEntry {
	final int pre;
	final String dbname;

	/**
	 * Create Index Entry from database node
	 *
	 * @param node
	 *            Database node
	 */
	IndexEntry(final ANode node) {
		pre = ((DBNode) node).pre();
		dbname = node.data().meta.name;
	}

	/**
	 * Index Entry
	 *
	 * @param dbn
	 *            database name as String
	 * @param p
	 *            pre value as int
	 * @deprecated removed in Version 1.3.0
	 */
	@Deprecated
	IndexEntry(final String dbn, final int p) {
		// TODO remove Ctor in GmlGeoX version 1.3.0
		pre = p;
		dbname = dbn;
	}
}
