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
package de.interactive_instruments.etf.bsxm.parser;

import java.util.Set;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

/**
 * The interface defines a handler of Element objects.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface BxElementHandler {

    enum ElementVisitResult {
        CONTINUE, SKIP_SUBTREE
    }

    /**
     * Returns a set of QNames this object will handle
     *
     * @return qnames that will be handled
     */
    Set<QName> elementsToRegister();

    ElementVisitResult onStart(final Element element, final BxReader reader);

    void onEnd(final Element element, final BxReader reader);
}
