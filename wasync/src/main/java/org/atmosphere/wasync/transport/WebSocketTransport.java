/*
 * Copyright 2015 Async-IO.org
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

import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.ws.WebSocket;
import com.ning.http.client.ws.WebSocketByteListener;
import com.ning.http.client.ws.WebSocketListener;
import com.ning.http.client.ws.WebSocketTextListener;
import com.ning.http.client.ws.WebSocketUpgradeHandler;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.atmosphere.wasync.Event.CLOSE;
import static org.atmosphere.wasync.Event.ERROR;
import static org.atmosphere.wasync.Event.HEADERS;
import static org.atmosphere.wasync.Event.MESSAGE;
import static org.atmosphere.wasync.Event.OPEN;
import static org.atmosphere.wasync.Event.REOPENED;
import static org.atmosphere.wasync.Event.STATUS;
import static org.atmosphere.wasync.Event.TRANSPORT;
import static org.atmosphere.wasync.Socket.STATUS;

/**
 * WebSocket {@link org.atmosphere.wasync.Transport} implementation
 *
 * @author Jeanfrancois Arcand
 */
public class WebSocketTransport extends WebSocketUpgradeHandler implements Transport {

    private final Logger logger = LoggerFactory.getLogger(WebSocketTransport.class);
    private WebSocket webSocket;

    private final AtomicBoolean ok = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger();
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private final List<FunctionWrapper> functions;
    private final List<Decoder<?, ?>> decoders;
    private final FunctionResolver resolver;
    private final Options options;
    private final RequestBuilder requestBuilder;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private STATUS status = Socket.STATUS.INIT;
    private final AtomicBoolean errorHandled = new AtomicBoolean();
    private Future underlyingFuture;
    private Future connectOperationFuture;
    protected final boolean protocolEnabled;
    protected boolean supportBinary = false;
    protected final ScheduledExecutorService timer;
    protected boolean protocolReceived = false;

    public WebSocketTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        super();
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
        this.supportBinary = options.binary() ||
                // Backward compatibility.
                (request.headers().get("Content-Type") != null ?
                        request.headers().get("Content-Type").contains("application/octet-stream") : false);

        protocolEnabled = request.queryString().get("X-atmo-protocol") != null;
        timer = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onThrowable(Throwable t) {
        logger.debug("", t);
        status = Socket.STATUS.ERROR;
        onFailure(t);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        status = Socket.STATUS.CLOSE;
        if (closed.getAndSet(true)) return;

        if (options.reconnectTimeoutInMilliseconds() <= 0 && !options.reconnect()) {
            timer.shutdown();
        }

        TransportsUtil.invokeFunction(CLOSE, decoders, functions, String.class, CLOSE.name(), CLOSE.name(), resolver);

        if (webSocket != null && webSocket.isOpen())
            webSocket.close();

        futureDone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATUS status() {
        return status;
    }

    void futureDone() {
        if (underlyingFuture != null) underlyingFuture.done();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean errorHandled() {
        return errorHandled.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void error(Throwable t) {
        logger.warn("", t);
        connectFutureException(t);
        TransportsUtil.invokeFunction(Event.ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
        logger.trace("Body received {}", new String(bodyPart.getBodyPartBytes()));
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        logger.trace("Status received {}", responseStatus);
        TransportsUtil.invokeFunction(STATUS, decoders, functions, Integer.class, new Integer(responseStatus.getStatusCode()), STATUS.name(), resolver);
        if (responseStatus.getStatusCode() == 101) {
            return STATE.UPGRADE;
        } else {
            logger.debug("Invalid status code {} for WebSocket Handshake", responseStatus.getStatusCode());
            status = Socket.STATUS.ERROR;
            throw new TransportNotSupported(responseStatus.getStatusCode(), responseStatus.getStatusText());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
        logger.trace("Headers received {}", headers);
        TransportsUtil.invokeFunction(HEADERS, decoders, functions, Map.class, headers.getHeaders(), HEADERS.name(), resolver);

        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket onCompleted() throws Exception {
        logger.trace("onCompleted {}", webSocket);
        if (webSocket == null) {
            logger.error("WebSocket Handshake Failed");
            status = Socket.STATUS.ERROR;
            return null;
        }
        TransportsUtil.invokeFunction(TRANSPORT, decoders, functions, Request.TRANSPORT.class, name(), TRANSPORT.name(), resolver);
        return webSocket;
    }

    void unlockFuture() {
        try {
            connectOperationFuture.finishOrThrowException();
        } catch (IOException e) {
            logger.warn("", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSuccess(WebSocket webSocket) {
        logger.trace("onSuccess {}", webSocket);

        this.webSocket = webSocket;

        if (connectOperationFuture != null && !protocolEnabled) {
            unlockFuture();
        }

        WebSocketListener l = new TextListener();
        if (supportBinary) {
            l = new BinaryListener(l);
        }
        webSocket.addWebSocketListener(l);
        l.onOpen(webSocket);
    }

    void connectFutureException(Throwable t) {
        IOException e = IOException.class.isAssignableFrom(t.getClass()) ? IOException.class.cast(t) : new IOException(t);
        connectOperationFuture.ioException(e).done();
    }


    void tryReconnect() {

        reconnectAttempt.incrementAndGet();

        if (options.reconnectTimeoutInMilliseconds() > 0) {
            timer.schedule(new Runnable() {
                public void run() {
                    reconnect();
                }
            }, options.reconnectTimeoutInMilliseconds(), TimeUnit.MILLISECONDS);
        } else {
            reconnect();
        }

    }

    void reconnect() {
        try {
            reconnecting.set(true);
            ok.set(false);
            
            status = Socket.STATUS.REOPENED;

            ListenableFuture<WebSocket> webSocketListenableFuture = options.runtime().executeRequest(requestBuilder.build(), WebSocketTransport.this);

            logger.info("try reconnect : attempt [{}/{}]", reconnectAttempt.get(), options.reconnectAttempts());

            webSocketListenableFuture.get();

            logger.info("reconnect successful ! in attempt [{}/{}]", reconnectAttempt.get(), options.reconnectAttempts());

            TransportsUtil.invokeFunction(REOPENED, decoders, functions, String.class, REOPENED.name(), REOPENED.name(), resolver);

            closed.set(false);
            reconnectAttempt.set(0);
            reconnecting.set(false);
        } catch (InterruptedException e) {
            reconnecting.set(false);
            logger.error("", e);
        } catch (ExecutionException e) {

            if (reconnectAttempt.get() < options.reconnectAttempts()) {
                tryReconnect();
            } else {
                reconnecting.set(false);
                reconnectAttempt.set(0);
                onFailure(e.getCause() != null ? e.getCause() : e);
            }
        }
    }

    public boolean touchSuccess() {
        return ok.getAndSet(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.WEBSOCKET;
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
    public final void onFailure(Throwable t) {

        if (!reconnecting.get()) {
            logger.trace("onFailure {}", t);
            connectFutureException(t);
            errorHandled.set(TransportsUtil.invokeFunction(ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver));
        }
    }

    public WebSocketTransport sendMessage(String message) {
        if (webSocket != null
                && !status.equals(Socket.STATUS.ERROR)
                && !status.equals(Socket.STATUS.CLOSE)) {
            webSocket.sendMessage(message);
        }
        return this;
    }

    public WebSocketTransport sendMessage(byte[] message) {
        if (webSocket != null
                && !status.equals(Socket.STATUS.ERROR)
                && !status.equals(Socket.STATUS.CLOSE)) {
            webSocket.sendMessage(message);
        }
        return this;
    }

    private final class TextListener implements WebSocketTextListener {
        @Override
        public void onMessage(String message) {
            logger.trace("onMessage {} for {}", message, webSocket);
            logger.trace("{} received {}", name(), message);
            if (protocolReceived || message.length() > 0) {
                TransportsUtil.invokeFunction(MESSAGE,
                        decoders,
                        functions,
                        message.getClass(),
                        message,
                        MESSAGE.name(),
                        resolver);

                // Since the protocol is enabled, handshake occurred, now ready so go asynchronous
                if (connectOperationFuture != null && protocolEnabled) {
                    unlockFuture();
                }
            }
            protocolReceived = true;
        }

        @Override
        public void onOpen(WebSocket websocket) {
            logger.trace("onOpen for {}", webSocket);

            // Could have been closed during the handshake.
            if (status.equals(Socket.STATUS.CLOSE) || status.equals(Socket.STATUS.ERROR)) return;

            closed.set(false);
            Event newStatus = status.equals(Socket.STATUS.INIT) ? OPEN : REOPENED;
            status = Socket.STATUS.OPEN;
            TransportsUtil.invokeFunction(newStatus,
                    decoders, functions, String.class, newStatus.name(), newStatus.name(), resolver);
        }

        @Override
        public void onClose(WebSocket websocket) {
            logger.trace("onClose for {}", webSocket);
            if (closed.get()) return;

            close();
            if (options.reconnect()) {
                tryReconnect();
            }
        }

        @Override
        public void onError(Throwable t) {
            logger.trace("onError for {}", t);
            status = Socket.STATUS.ERROR;
            logger.debug("", t);
            onFailure(t);
        }
    }

    private final class BinaryListener implements WebSocketByteListener {

        private final WebSocketListener l;

        private BinaryListener(WebSocketListener l) {
            this.l = l;
        }

        @Override
        public void onMessage(byte[] message) {
            logger.trace("{} received {}", name(), message);
            if (protocolReceived || (message.length > 0 && !Utils.whiteSpace(message))) {
                TransportsUtil.invokeFunction(MESSAGE,
                        decoders,
                        functions,
                        message.getClass(),
                        message,
                        MESSAGE.name(),
                        resolver);

                // Since the protocol is enabled, handshake occurred, now ready so go asynchronous
                if (connectOperationFuture != null && protocolEnabled) {
                    unlockFuture();
                }
            }
            protocolReceived = true;
        }

        @Override
        public void onOpen(WebSocket websocket) {
            l.onOpen(websocket);
        }

        @Override
        public void onClose(WebSocket websocket) {
            l.onClose(websocket);
        }

        @Override
        public void onError(Throwable t) {
            l.onError(t);
        }
    }
}
