/*
 * Copyright 2013 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.wasync.serial;

import com.google.common.util.concurrent.SettableFuture;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Default implementation of a {@link SerializedFireStage}.
 * <p/>
 * This implementation is based on an unbounded stage that enqueues the payload
 * objects to be fired ({@link SerializedFireStage#enqueue(Object, SettableFuture)}) by
 * means of a {@link LinkedBlockingQueue}.
 * <p/>
 * Every instance of this class spans its dedicated stage thread, which sequentially
 * consumes payload objects off the stage queue.
 * <p/>
 * Binary payloads are aggregated up to a {@code maxBinaryPayloadAggregationSize}.
 * <p/>
 *
 * @author Christian Bach
 */
public class DefaultSerializedFireStage implements SerializedFireStage {

    private volatile SerializedSocket socket;
    private final int maxBinaryMessagesAggregationSize;

    private final BlockingQueue<FirePayloadEntry> firePayloadsQueue;
    private final ExecutorService executorService;
    private final Runnable fireTask;

    public DefaultSerializedFireStage() {
        this(4);
    }

    public DefaultSerializedFireStage(int maxBinaryPayloadAggregationSize) {
        this.maxBinaryMessagesAggregationSize = maxBinaryPayloadAggregationSize;
        firePayloadsQueue = new LinkedBlockingQueue<FirePayloadEntry>();
        executorService = Executors.newSingleThreadExecutor();
        fireTask = createFireTask();
        executorService.execute(fireTask);
    }

    @Override
    public void setSocket(SerializedSocket socket) {
        this.socket = socket;
    }

    @Override
    public void enqueue(Object firePayload, SettableFuture<Response> originalFuture) {
        firePayloadsQueue.add(new FirePayloadEntry(firePayload, originalFuture));
    }

    private Runnable createFireTask() {
        return new Runnable() {
            public void run() {
                ArrayList<FirePayloadEntry> aggregatedByteArrayPayloads = new ArrayList<FirePayloadEntry>(maxBinaryMessagesAggregationSize);
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        FirePayloadEntry payloadEntry = firePayloadsQueue.take();
                        int aggregationCount = 0;

                        for (; aggregationCount < maxBinaryMessagesAggregationSize; aggregationCount++) {
                            if (byte[].class.isAssignableFrom(payloadEntry.getFirePayload().getClass())) {
                                aggregatedByteArrayPayloads.add(payloadEntry);
                                payloadEntry = firePayloadsQueue.poll();
                                if (payloadEntry == null) {
                                    aggregationCount = 0;
                                    break;
                                }
                            } else {
                                if (!aggregatedByteArrayPayloads.isEmpty()) {
                                    fireSynchronously(aggregatedByteArrayPayloads);
                                    aggregatedByteArrayPayloads.clear();
                                }
                                fireSynchronously(payloadEntry);
                                aggregationCount = 0;
                                break;
                            }
                        }

                        if (!aggregatedByteArrayPayloads.isEmpty()) {
                            fireSynchronously(aggregatedByteArrayPayloads);
                            aggregatedByteArrayPayloads.clear();
                        }

                    }
                } catch (InterruptedException consumed) {
                    // allow thread to exit
                }
            }
        };
    }

    private void fireSynchronously(ArrayList<FirePayloadEntry> aggregatedByteArrayPayloads) {
        ListenableFuture<Response> future;
        int aggregatedSize = 0;
        for (FirePayloadEntry entry : aggregatedByteArrayPayloads) {
            aggregatedSize += ((byte[]) entry.getFirePayload()).length;
        }
        byte[] aggregatedByteArray = new byte[aggregatedSize];
        int destPos = 0;
        for (FirePayloadEntry entry : aggregatedByteArrayPayloads) {
            byte[] payload = (byte[]) entry.getFirePayload();
            System.arraycopy(
                    payload, 0,
                    aggregatedByteArray, destPos,
                    payload.length);
            destPos += payload.length;
        }


        Response response = null;
        try {
            future = socket.directWrite(aggregatedByteArray);
            response = future.get();
        } catch (Exception e) {
            for (FirePayloadEntry entry : aggregatedByteArrayPayloads) {
                entry.getOriginalFuture().setException(e);
                entry.getOriginalFuture().cancel(true);
            }
        } finally {
            for (FirePayloadEntry entry : aggregatedByteArrayPayloads) {
                entry.getOriginalFuture().set(response);
            }
        }
    }

    public void fireSynchronously(FirePayloadEntry firePayloadEntry) {
        ListenableFuture<Response> future;
        Response response = null;
        try {
            future = socket.directWrite(firePayloadEntry.firePayload);
            response = future.get();
        } catch (Exception e) {
            firePayloadEntry.getOriginalFuture().setException(e);
            firePayloadEntry.getOriginalFuture().cancel(true);
        } finally {
            firePayloadEntry.getOriginalFuture().set(response);
        }
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
        for (FirePayloadEntry entry : firePayloadsQueue) {
            entry.getOriginalFuture().cancel(true);
        }
    }

    private class FirePayloadEntry {

        private Object firePayload;
        private SettableFuture<Response> originalFuture;

        public FirePayloadEntry(Object firePayload, SettableFuture<Response> originalFuture) {
            this.firePayload = firePayload;
            this.originalFuture = originalFuture;
        }

        public Object getFirePayload() {
            return firePayload;
        }

        public SettableFuture<Response> getOriginalFuture() {
            return originalFuture;
        }

    }

}
