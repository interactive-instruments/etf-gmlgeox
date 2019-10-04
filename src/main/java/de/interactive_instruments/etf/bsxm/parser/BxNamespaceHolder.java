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
package de.interactive_instruments.etf.bsxm.parser;

import java.util.HashMap;

import org.basex.query.QueryContext;
import org.basex.util.Token;
import org.jetbrains.annotations.NotNull;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class BxNamespaceHolder {
    private final HashMap<String, String> prefixNamespaceMapping = new HashMap<>();
    private final HashMap<String, String> namespacePrefixMapping = new HashMap<>();

    public BxNamespaceHolder() {}

    public BxNamespaceHolder define(final String prefix, final String namespaceUri) {
        this.prefixNamespaceMapping.put(prefix, namespaceUri);
        this.namespacePrefixMapping.put(namespaceUri, prefix);
        return this;
    }

    public String prefix(final String namespaceUri) {
        return this.namespacePrefixMapping.get(namespaceUri);
    }

    public String namespace(final String prefix) {
        return this.prefixNamespaceMapping.get(prefix);
    }

    public static BxNamespaceHolder init(@NotNull final QueryContext queryContext) {
        if (queryContext.context.data() != null) {
            final int gmlIdIndex = queryContext.context.data().nspaces.uriIdForPrefix("gml".getBytes(), 1,
                    queryContext.context.data());
            if (gmlIdIndex > 0) {
                return new BxNamespaceHolder().define("gml",
                        Token.string(queryContext.context.data().nspaces.uri(gmlIdIndex)));
            }
        }
        // fallback
        return new BxNamespaceHolder().define("gml", "http://www.opengis.net/gml/3.2");
    }
}
