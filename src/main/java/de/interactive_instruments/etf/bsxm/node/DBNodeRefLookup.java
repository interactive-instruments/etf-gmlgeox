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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.basex.query.QueryContext;
import org.basex.query.value.node.DBNode;
import org.jetbrains.annotations.NotNull;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * The class encapsulates the lookup of Database nodes from DBNodeRefs
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
final public class DBNodeRefLookup {
    private final QueryContext qc;
    private final DBNodeRefFactory factory;

    private ExecutorService executors = Executors
            .newFixedThreadPool(1, new ThreadFactoryBuilder().setNameFormat("GmlGeoX SubscribeOn-%d").build());

    public DBNodeRefLookup(final QueryContext qc, final DBNodeRefFactory factory) {
        this.qc = qc;
        this.factory = factory;
    }

    @NotNull
    public DBNode resolve(@NotNull final DBNodeRef ref) {
        return ref.resolve(qc, factory);
    }

    @NotNull
    public List<DBNode> collect(@NotNull final Observable<DBNodeRef> observer) {
        final List<DBNode> nodelist = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        observer.subscribeOn(Schedulers.from(executors)).subscribe(
                new Subscriber<DBNodeRef>() {
                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }

                    @Override
                    public void onError(final Throwable e) {
                        latch.countDown();
                    }

                    @Override
                    public void onNext(final DBNodeRef nodeRef) {
                        nodelist.add(nodeRef.resolve(qc, factory));
                    }
                });
        if (latch.getCount() != 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted while waiting for search to complete.", e);
            }
        }
        return nodelist;
    }
}
