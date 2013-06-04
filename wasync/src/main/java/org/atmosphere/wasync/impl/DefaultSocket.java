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
package org.atmosphere.wasync.impl;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.FluentStringsMap;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.websocket.WebSocket;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.transport.LongPollingTransport;
import org.atmosphere.wasync.transport.SSETransport;
import org.atmosphere.wasync.transport.StreamTransport;
import org.atmosphere.wasync.transport.TransportNotSupported;
import org.atmosphere.wasync.transport.WebSocketTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default implementation of the {@link org.atmosphere.wasync.Socket}
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultSocket implements Socket {

    private final static Logger logger = LoggerFactory.getLogger(DefaultSocket.class);

    protected Request request;
    protected SocketRuntime socketRuntime;
    protected final List<FunctionWrapper> functions = new ArrayList<FunctionWrapper>();
    protected Transport transportInUse;
    protected final Options options;

    public DefaultSocket(Options options) {
        this.options = options;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future fire(Object data) throws IOException {
        checkState();
        if (transportInUse.status().equals(STATUS.CLOSE) ||
                transportInUse.status().equals(STATUS.ERROR)) {
            transportInUse.error(new IOException("Invalid Socket Status " + transportInUse.status().name()));
            return socketRuntime.rootFuture;
        }

        return socketRuntime.write(request, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket on(Function<? extends Object> function) {
        return on("", function);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket on(String functionName, Function<? extends Object> function) {
        functions.add(new FunctionWrapper(functionName, function));
        return this;
    }

    public Socket open(Request request) throws IOException {
        return open(request, -1, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket open(Request request, long timeout, TimeUnit tu) throws IOException {
        this.request = request;
        RequestBuilder r = new RequestBuilder();
        r.setUrl(request.uri())
                .setMethod(request.method().name())
                .setHeaders(request.headers())
                .setQueryParameters(decodeQueryString(request));

        List<Transport> transports = getTransport(r, request);

        return connect(r, transports, timeout, tu);
    }

    static FluentStringsMap decodeQueryString(Request request) {
        Map<String, List<String>> c = request.queryString();
        FluentStringsMap f = new FluentStringsMap();
        f.putAll(c);
        return f;
    }

    protected Socket connect(final RequestBuilder r, final List<Transport> transports) throws IOException {
        return connect(r, transports, -1, TimeUnit.MILLISECONDS);
    }

    protected Socket connect(final RequestBuilder r, final List<Transport> transports, final long timeout, final TimeUnit tu) throws IOException {

        if (transports.size() > 0) {
            transportInUse = transports.get(0);
        } else {
            throw new IOException("No suitable transport supported");
        }
        socketRuntime = new SocketRuntime(options, new DefaultFuture(this), functions);

        functions.add(new FunctionWrapper("", new Function<TransportNotSupported>() {
            @Override
            public void on(TransportNotSupported transportNotSupported) {
                request.transport().remove(0);
                if (request.transport().size() > 0) {
                    try {
                        if (request.queryString().get("X-Atmosphere-Transport") != null) {
                            Request.TRANSPORT rt = request.transport().get(0);
                            String t = rt == Request.TRANSPORT.LONG_POLLING ? "long-polling" : rt.name();
                            request.queryString().put("X-Atmosphere-Transport", Arrays.asList(new String[]{t}));
                        }
                        open(request, timeout, tu);
                    } catch (IOException e) {
                        logger.error("", e);
                    }
                } else {
                    throw new Error("No suitable transport supported by the server");
                }
            }
        }));

        if (transportInUse.name().equals(Request.TRANSPORT.WEBSOCKET)) {
            r.setUrl(request.uri().replace("http", "ws"));
            try {
                java.util.concurrent.Future<WebSocket> fw = options.runtime().prepareRequest(r.build()).execute(
                        (AsyncHandler<WebSocket>) transportInUse);

                fw.get(timeout, tu);
                socketRuntime = new SocketRuntime(WebSocketTransport.class.cast(transportInUse), options, socketRuntime.future(), functions);
            } catch (ExecutionException t) {
                Throwable e = t.getCause();

                if (TransportNotSupported.class.isAssignableFrom(e.getClass())) {
                    return this;
                }

                transportInUse.close();
                closeRuntime(true);
                if (!transportInUse.errorHandled() && TimeoutException.class.isAssignableFrom(e.getClass())) {
                    transportInUse.error(new IOException("Invalid state: " + e.getMessage()));
                }

                return new VoidSocket();
            } catch (Throwable t) {
                transportInUse.onThrowable(t);
                return new VoidSocket();
            }
        } else {
            r.setUrl(request.uri().replace("ws", "http"));
            options.runtime().prepareRequest(r.build()).execute((AsyncHandler<String>) transportInUse);

            try {
                // TODO: Give a chance to connect and then unlock. With Atmosphere we will received junk at the
                // beginning for streaming and sse, but nothing for long-polling
                socketRuntime.future().get(options.waitBeforeUnlocking(), TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                // Swallow  LOG ME
                logger.trace("", t);
            }

            socketRuntime = createSocket();
        }
        return this;
    }

    protected SocketRuntime createSocket() {
        return new SocketRuntime(options, new DefaultFuture(this), functions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // Not connected, but close the underlying AHC.
        if (transportInUse == null) {
            closeRuntime(false);
        } else if (socketRuntime != null && !transportInUse.status().equals(STATUS.CLOSE)) {
            transportInUse.close();
            closeRuntime(true);
        }
    }

    void closeRuntime(boolean async) {
        if (!options.runtimeShared() && !options.runtime().isClosed()) {
            if (async) {
                // AHC is broken when calling closeAsynchronously.
                // https://github.com/AsyncHttpClient/async-http-client/issues/290
                final ExecutorService e= Executors.newSingleThreadExecutor();
                e.submit(new Runnable() {
                    @Override
                    public void run() {
                        options.runtime().close();
                        e.shutdown();
                    }
                });
            }
            else
                options.runtime().close();
        } else if (options.runtimeShared()) {
            logger.warn("Cannot close underlying AsyncHttpClient because it is shared. Make sure you close it manually.");
        }
    }

    @Override
    public Socket.STATUS status() {
        if (transportInUse == null) {
            return STATUS.CLOSE;
        } else {
            return transportInUse.status();
        }
    }

    protected SocketRuntime internalSocket() {
        return socketRuntime;
    }

    protected List<Transport> getTransport(RequestBuilder r, Request request) throws IOException {
        List<Transport> transports = new ArrayList<Transport>();

        if (request.transport().size() == 0) {
            transports.add(new WebSocketTransport(r, options, request, functions));
            transports.add(new LongPollingTransport(r, options, request, functions));
        }

        for (Request.TRANSPORT t : request.transport()) {
            if (t.equals(Request.TRANSPORT.WEBSOCKET)) {
                transports.add(new WebSocketTransport(r, options, request, functions));
            } else if (t.equals(Request.TRANSPORT.SSE)) {
                transports.add(new SSETransport(r, options, request, functions));
            } else if (t.equals(Request.TRANSPORT.LONG_POLLING)) {
                transports.add(new LongPollingTransport(r, options, request, functions));
            } else if (t.equals(Request.TRANSPORT.STREAMING)) {
                transports.add(new StreamTransport(r, options, request, functions));
            }
        }
        return transports;
    }


    protected Request request() {
        return request;
    }

    private final static class VoidSocket implements Socket {

        @Override
        public Future fire(Object data) throws IOException {
            throw new IllegalStateException("An error occurred during connection. Please add a Function(Throwable) to debug.");
        }

        @Override
        public Socket on(Function<? extends Object> function) {
            throw new IllegalStateException("An error occurred during connection. Please add a Function(Throwable) to debug.");
        }

        @Override
        public Socket on(String functionMessage, Function<? extends Object> function) {
            throw new IllegalStateException("An error occurred during connection. Please add a Function(Throwable) to debug.");
        }

        @Override
        public Socket open(Request request) throws IOException {
            throw new IllegalStateException("An error occurred during connection. Please add a Function(Throwable) to debug.");
        }

        @Override
        public void close() {
            throw new IllegalStateException("An error occurred during connection. Please add a Function(Throwable) to debug.");
        }

        @Override
        public STATUS status() {
            return STATUS.ERROR;
        }

        @Override
        public Socket open(Request request, long timeout, TimeUnit tu) throws IOException {
            throw new IllegalStateException("An error occured during connection. Please add a Function(Throwable) to debug.");
        }
    }

    void checkState() {
        if (transportInUse == null) {
            throw new IllegalStateException("Invalid Socket Status : Not Connected");
        }
    }
}
