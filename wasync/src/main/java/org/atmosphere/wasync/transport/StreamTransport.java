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
package org.atmosphere.wasync.transport;

import static org.atmosphere.wasync.Event.CLOSE;
import static org.atmosphere.wasync.Event.ERROR;
import static org.atmosphere.wasync.Event.HEADERS;
import static org.atmosphere.wasync.Event.MESSAGE;
import static org.atmosphere.wasync.Event.OPEN;
import static org.atmosphere.wasync.Event.REOPENED;
import static org.atmosphere.wasync.Event.STATUS;
import static org.atmosphere.wasync.Event.TRANSPORT;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.RequestBuilder;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Socket.STATUS;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.util.FluentStringsMap;
import org.atmosphere.wasync.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * Streaming {@link org.atmosphere.wasync.Transport} implementation
 *
 * @author Jeanfrancois Arcand
 */
public class StreamTransport implements AsyncHandler<String>, Transport {
    private final static String DEFAULT_CHARSET = "UTF-8";
    private final Logger logger = LoggerFactory.getLogger(StreamTransport.class);

    protected final List<FunctionWrapper> functions;
    protected final List<Decoder<? extends Object, ?>> decoders;
    //TODO fix me
    protected String charSet = DEFAULT_CHARSET;
    protected final FunctionResolver resolver;
    protected final Options options;
    protected final RequestBuilder requestBuilder;
    protected final Request request;
    protected final AtomicBoolean closed = new AtomicBoolean(false);
    protected final boolean isBinary;
    protected STATUS status = Socket.STATUS.INIT;
    protected final AtomicBoolean errorHandled = new AtomicBoolean();
    protected Future underlyingFuture;
    protected Future connectOperationFuture;
    protected final boolean protocolEnabled;
    protected final ScheduledExecutorService timer;
    protected boolean protocolReceived = false;

    public StreamTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        this.decoders = request.decoders();

        if (decoders.size() == 0) {
            decoders.add(new Decoder<String, Object>() {
                @Override
                public Object decode(Event e, String s) {
                    return s;
                }
            });
        }
        this.functions = functions;
        this.resolver = request.functionResolver();
        this.options = options;
        this.requestBuilder = requestBuilder;
        this.request = request;

        protocolEnabled = request.queryString().get("X-atmo-protocol") != null;
        isBinary = options.binary() ||
                // Backward compatibility.
                (request.headers().get("Content-Type") != null ?
                        request.headers().get("Content-Type").contains("application/octet-stream") : false);

        timer = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transport registerF(FunctionWrapper function) {
        functions.add(function);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onThrowable(Throwable t) {
        if (CancellationException.class.isAssignableFrom(t.getClass())) return;

        if(request != null) {
        	logger.warn("StreamTransport notified with exception {} for request : {}", t, request.uri());
        }
        logger.warn("", t);
        status = Socket.STATUS.ERROR;
        connectFutureException(t);

        errorHandled.set(TransportsUtil.invokeFunction(ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
        if (isBinary) {
            byte[] payload = bodyPart.getBodyPartBytes();

            if (!protocolReceived && !Utils.whiteSpace(payload)) {
                protocolReceived = true;
            }

            if (protocolReceived) {
                TransportsUtil.invokeFunction(decoders, functions, payload.getClass(), payload, MESSAGE.name(), resolver);
                unlockFuture();
            }
        } else {
            String m = new String(bodyPart.getBodyPartBytes(), charSet);

            if (!protocolReceived) {
                m = m.trim();
                if (m.length() > 0) {
                    protocolReceived = true;
                }
            }

            if (protocolReceived && !bodyPart.isLast()) {
                TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, MESSAGE.name(), resolver);
                unlockFuture();
            }
        }

        return State.CONTINUE;
    }

    void unlockFuture() {
        // Since the protocol is enabled, handshake occurred, now ready so go asynchronous
        if (connectOperationFuture != null && protocolEnabled) {
            triggerOpen();
            try {
                connectOperationFuture.finishOrThrowException();
            } catch (IOException e) {
                logger.warn("", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State onHeadersReceived(HttpHeaders headers) throws Exception {
    	Map<String, String> headerMap = new HashMap<>();
		for (Map.Entry<String, String> entry : headers) {
			headerMap.put(entry.getKey(), entry.getValue());
		}
        TransportsUtil.invokeFunction(HEADERS, decoders, functions, Map.class, headerMap, HEADERS.name(), resolver);

        // TODO: Parse charset
        return AsyncHandler.State.CONTINUE;
    }

    void futureDone() {
        if (underlyingFuture != null) underlyingFuture.done();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncHandler.State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        if (connectOperationFuture != null && !protocolEnabled) {
            connectOperationFuture.finishOrThrowException();
        }

        TransportsUtil.invokeFunction(TRANSPORT, decoders, functions, Request.TRANSPORT.class, name(), TRANSPORT.name(), resolver);

        errorHandled.set(false);
        closed.set(false);

        if (!protocolEnabled) {
            triggerOpen();
        }

        TransportsUtil.invokeFunction(MESSAGE, decoders, functions, Integer.class, new Integer(responseStatus.getStatusCode()), STATUS.name(), resolver);

        return State.CONTINUE;
    }

    void triggerOpen() {
        Event newStatus = status.equals(Socket.STATUS.INIT) ? OPEN : REOPENED;
        status = Socket.STATUS.OPEN;
        TransportsUtil.invokeFunction(newStatus,
                decoders, functions, String.class, newStatus.name(), newStatus.name(), resolver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String onCompleted() throws Exception {
        futureDone();

        if (closed.get()) return "";

        if (status == Socket.STATUS.ERROR) {
            return "";
        }

        if (options.reconnect()) {
            close(false);
            if (options.reconnectTimeoutInMilliseconds() > 0) {
                timer.schedule(new Runnable() {
                    public void run() {
                        status = Socket.STATUS.REOPENED;
                        reconnect();
                    }
                }, options.reconnectTimeoutInMilliseconds(), TimeUnit.MILLISECONDS);
            } else {
                status = Socket.STATUS.REOPENED;
                reconnect();
            }
        } else {
            close();
        }
        return "";
    }

    void reconnect() {
        Map<String, List<String>> c = request.queryString();
        FluentStringsMap f = new FluentStringsMap();
        f.putAll(c);
        options.runtime().executeRequest(requestBuilder.setQueryParams(f).build(), StreamTransport.this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.STREAMING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        close(true);
    }


    private void close(boolean force) {
        if (force && closed.getAndSet(true)) return;

        status = Socket.STATUS.CLOSE;

        if (force) {
            timer.shutdown();
        }

        TransportsUtil.invokeFunction(CLOSE, decoders, functions, String.class, CLOSE.name(), CLOSE.name(), resolver);

        if (underlyingFuture != null) underlyingFuture.cancel(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATUS status() {
        return status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean errorHandled() {
        return errorHandled.get();
    }

    void connectFutureException(Throwable t) {
        IOException e = IOException.class.isAssignableFrom(t.getClass()) ? IOException.class.cast(t) : new IOException(t);
        connectOperationFuture.ioException(e);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void error(Throwable t) {
        logger.warn("", t);
        connectFutureException(t);
        TransportsUtil.invokeFunction(ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void future(Future f) {
        this.underlyingFuture = f;
    }

    @Override
    public void connectedFuture(Future f) {
        this.connectOperationFuture = f;
    }
}

