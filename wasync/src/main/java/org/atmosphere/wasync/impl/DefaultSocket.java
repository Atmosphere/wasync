/*
 * Copyright 2017 Async-IO.org
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.ws.WebSocket;
import org.atmosphere.wasync.Event;
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
import org.atmosphere.wasync.util.FluentStringsMap;
import org.atmosphere.wasync.util.FutureProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        if (transportInUse.name().equals(Request.TRANSPORT.WEBSOCKET)
            && transportInUse.status().equals(STATUS.CLOSE) ||
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

    @Override
    public Socket on(Event event, Function<?> function) {
        return on(event.name(), function);
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
                .setQueryParams(decodeQueryString(request));

        List<Transport> transports = getTransport(r, request);

        return connect(r, transports, timeout, tu);
    }

    static FluentStringsMap decodeQueryString(Request request) {
        Map<String, List<String>> c = request.queryString();
        FluentStringsMap f = new FluentStringsMap();
        f.putAll(c);
        return f;
    }

    protected Socket connect(final RequestBuilder r, final List<Transport> transports, long timeout, final TimeUnit tu) throws IOException {

        if (transports.size() > 0) {
            transportInUse = transports.get(0);
        } else {
            throw new IOException("No suitable transport supported");
        }
        DefaultFuture f = new DefaultFuture(this);
        socketRuntime = createRuntime(f, options, functions);
        transportInUse.connectedFuture(f);
        timeout = timeout == -1 ? Long.MAX_VALUE : timeout;

        addFunction(timeout, tu);

        try {
            if (transportInUse.name().equals(Request.TRANSPORT.WEBSOCKET)) {
                r.setUrl(webSocketUrl(request.uri()));
                try {
                    transportInUse.future(new FutureProxy<ListenableFuture>(this,
                            options.runtime().prepareRequest(r.build()).execute((AsyncHandler<WebSocket>) transportInUse)));

                    logger.trace("WebSocket Connect Timeout {}", timeout);
                    f.get(timeout, tu);
                } catch (ExecutionException t) {
                    Throwable e = t.getCause();

                    logger.error("Unable to open url {}", request.uri(), t);

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
                    logger.error("Unable to open url {}", request.uri(), t);
                    transportInUse.onThrowable(t);
                    return new VoidSocket();
                }
            } else {
	            r.setUrl(httpUrl(request.uri()));
                transportInUse.future(new FutureProxy<ListenableFuture>(this,
                        options.runtime().prepareRequest(r.build()).execute((AsyncHandler<String>) transportInUse)));

                logger.debug("Http Connect Timeout {}", timeout);
                try {
                    if (options.waitBeforeUnlocking() > 0) {
                        logger.info("Waiting {}, allowing the http connection to get handled by the server. To reduce the delay," +
                                " make sure some bytes get written when the connection is suspended on the server", options.waitBeforeUnlocking());
                    }

                    f.get(options.waitBeforeUnlocking(), TimeUnit.MILLISECONDS);
                } catch (Throwable t) {
                    // Swallow the exception as this could be expected.
                    logger.trace("", t);
                }
            }
        } finally {
            f.finishOrThrowException();
        }
        return this;
    }

    private String webSocketUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://") ? "ws" + url.substring(4) : url;
    }

    private String httpUrl(String url) {
        return url.startsWith("ws://") || url.startsWith("wss://") ? "http" + url.substring(2) : url;
    }

    protected void addFunction(final long timeout, final TimeUnit tu) {
        functions.add(new FunctionWrapper("", new Function<TransportNotSupported>() {
            @Override
            public void on(TransportNotSupported transportNotSupported) {
                request.transport().remove(0);
                if (request.transport().size() > 0) {
                    try {
                        open(request, timeout, tu);
                    } catch (IOException e) {
                        logger.error("", e);
                    }
                } else {
                    throw new Error("No suitable transport supported by the server");
                }
            }
        }));
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

    protected void closeRuntime(boolean async) {
    	if (!options.runtime().isClosed()) {
	        if (!options.runtimeShared()) {
	            if (async) {
	                // AHC is broken when calling closeAsynchronously.
	                // https://github.com/AsyncHttpClient/async-http-client/issues/290
	                final ExecutorService e = Executors.newSingleThreadExecutor();
	                e.submit(new Runnable() {
	                    @Override
	                    public void run() {
	    	            	//TODO fix try catch
	                        try {
								options.runtime().close();
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
	                        e.shutdown();
	                    }
	                });
	            } else {
	            	
	            	//TODO fix try catch
	                try {
						options.runtime().close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	            }
	        } else {
	            logger.warn("Cannot close underlying AsyncHttpClient because it is shared. Make sure you close it manually.");
	        }
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
        public Socket on(Event event, Function<?> function) {
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

    public SocketRuntime createRuntime(DefaultFuture future, Options options, List<FunctionWrapper> functions) {
        return new SocketRuntime(transportInUse, options, future, functions);
    }
}
