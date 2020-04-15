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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A factory to create flyweight DBNodeRef instances.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
final class DBNodeRefDefaultFactory implements DBNodeRefFactory {

    final String databaseNamePrefix;
    final int dbNameLength;

    @Contract("null -> fail")
    DBNodeRefDefaultFactory(final String databaseNamePrefix) {
        if (databaseNamePrefix == null || databaseNamePrefix.length() < 4) {
            throw new IllegalArgumentException("Invalid database name: '" + databaseNamePrefix + "'. "
                    + "Database names must be suffixed with a three digits index, i.e. DB-000");
        }
        final int length = databaseNamePrefix.length();
        for (int i = length - 1; i >= length - 3; i--) {
            if (databaseNamePrefix.charAt(i) < '0' || databaseNamePrefix.charAt(i) > '9') {
                throw new IllegalArgumentException("Invalid database name: '" + databaseNamePrefix + "'. "
                        + "Database names must be suffixed with a three digits index, i.e. DB-000");
            }
        }
        this.databaseNamePrefix = databaseNamePrefix.substring(0, length - 3);
        this.dbNameLength = length;
    }

    @NotNull
    @Override
    public StringBuilder getSBForDbNamePrefix() {
        final StringBuilder sb = new StringBuilder(this.databaseNamePrefix.length() + 3);
        sb.append(this.databaseNamePrefix);
        return sb;
    }

    @Contract(pure = true)
    @Override
    public String getDbNamePrefix() {
        return databaseNamePrefix;
    }

    @NotNull
    @Contract("_ -> new")
    @Override
    public DBNodeRef createDBNodeEntry(@NotNull final DBNode node) {
        final String name = node.data().meta.name;
        final byte dbIndex = (byte) ((name.charAt(dbNameLength - 1) - '0') +
                (name.charAt(dbNameLength - 2) - '0') * 10 +
                (name.charAt(dbNameLength - 3) - '0') * 100);
        return new DBNodeRef(node, dbIndex);
    }
}
