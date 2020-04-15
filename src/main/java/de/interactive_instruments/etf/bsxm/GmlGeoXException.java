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
package de.interactive_instruments.etf.bsxm;

import org.basex.query.QueryException;
import org.deegree.commons.xml.XMLParsingException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class GmlGeoXException extends QueryException {

    GmlGeoXException(@NotNull final XMLParsingException parsingExcpetion) {
        super(parsingExcpetion.getMessage());
    }

    GmlGeoXException(final String message) {
        super(message);
    }

    GmlGeoXException(final String message, final Throwable cause) {
        super(message);
        // looks like QueryException does not provide a constructor that takes both a message and a cause
        this.initCause(cause);
    }

    GmlGeoXException(final Throwable cause) {
        super(cause);
    }
}
