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

import java.util.AbstractList;
import java.util.List;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class ListRevWrapper<E> extends AbstractList<E> {

    private final List<E> wrappedList;

    public ListRevWrapper(List<E> wrappedList) {
        this.wrappedList = wrappedList;
    }

    @Override
    public E get(int i) {
        return wrappedList.get(wrappedList.size() - i - 1);
    }

    @Override
    public int size() {
        return wrappedList.size();
    }

    public List<E> normalView() {
        return wrappedList;
    }
}
