/**
 * Copyright 2010-2020 interactive instruments GmbH
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
package de.interactive_instruments.etf.bsxm.node;

import org.basex.query.value.node.DBNode;

/**
 * A factory to create flyweight DBNodeRef instances.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface DBNodeRefFactory {

    static DBNodeRefFactory create(final String dbName) {
        return new DBNodeRefDefaultFactory(dbName);
    }

    DBNodeRef createDBNodeEntry(final DBNode node);

    StringBuilder getSBForDbNamePrefix();

    String getDbNamePrefix();
}
