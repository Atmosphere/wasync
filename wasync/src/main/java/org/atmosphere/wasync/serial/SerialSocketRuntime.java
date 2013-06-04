/*
 * Copyright 2012 Jeanfrancois Arcand
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
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.DefaultFuture;
import org.atmosphere.wasync.impl.SocketRuntime;
import org.atmosphere.wasync.transport.WebSocketTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Serial extension for the {@link SocketRuntime}
 *
 * @author Jeanfrancois Arcand
 *
 */
public class SerialSocketRuntime extends SocketRuntime {

    private final static Logger logger = LoggerFactory.getLogger(SerialSocketRuntime.class);
    private final SerializedSocket serializedSocket;

    public SerialSocketRuntime(WebSocketTransport webSocket, Options options, DefaultFuture rootFuture, SerializedSocket serializedSocket,  List<FunctionWrapper> functions) {
        super(webSocket, options, rootFuture, functions);
        this.serializedSocket = serializedSocket;
    }

    public SerialSocketRuntime(Options options, DefaultFuture rootFuture, SerializedSocket serializedSocket, List<FunctionWrapper> functions) {
        this(null, options, rootFuture, serializedSocket, functions);
    }

    public Future write(Request request, Object data) throws IOException {

        if (webSocketTransport != null) {
            Object object = invokeEncoder(request.encoders(), data);
            webSocketWrite(request, object, data);
        } else {
            // Execute encoder
            Object encodedPayload = invokeEncoder(request.encoders(), data);
            if (!(InputStream.class.isAssignableFrom(encodedPayload.getClass())
                            || Reader.class.isAssignableFrom(encodedPayload.getClass())
                            || String.class.isAssignableFrom(encodedPayload.getClass())
                            || byte[].class.isAssignableFrom(encodedPayload.getClass())
                    )) {
                throw new IllegalStateException("No Encoder for " + data);
            }

            if (serializedSocket.getSerializedFireStage() != null) {
                final SettableFuture<Response> future = SettableFuture.create();
                serializedSocket.getSerializedFireStage().enqueue(encodedPayload, future);
                return new Future() {

                    @Override
                    public Future fire(Object data) throws IOException {
                        return serializedSocket.fire(data);
                    }

                    @Override
                    public Future done() {
                        ListenableFuture.class.cast(future).done(null);
                        return this;
                    }

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return future.cancel(mayInterruptIfRunning);
                    }

                    @Override
                    public boolean isCancelled() {
                        return future.isCancelled();
                    }

                    @Override
                    public boolean isDone() {
                        return future.isDone();
                    }

                    @Override
                    public Socket get() throws InterruptedException, ExecutionException {
                        future.get();
                        return serializedSocket;
                    }

                    @Override
                    public Socket get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                        future.get(timeout, unit);
                        return serializedSocket;
                    }
                };
            } else {
                final ListenableFuture<Response> future = serializedSocket.directWrite(encodedPayload);
                return new Future() {
                    @Override
                    public Future fire(Object data) throws IOException {
                        return serializedSocket.fire(data);
                    }

                    @Override
                    public Future done() {
                        future.done(null);
                        return this;
                    }

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return future.cancel(mayInterruptIfRunning);
                    }

                    @Override
                    public boolean isCancelled() {
                        return future.isCancelled();
                    }

                    @Override
                    public boolean isDone() {
                        return future.isDone();
                    }

                    @Override
                    public Socket get() throws InterruptedException, ExecutionException {
                        future.get();
                        return serializedSocket;
                    }

                    @Override
                    public Socket get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                        future.get(timeout, unit);
                        return serializedSocket;
                    }
                };
            }
        }
        return rootFuture;
    }
}