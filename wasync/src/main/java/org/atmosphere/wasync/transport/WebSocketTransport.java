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
package org.atmosphere.wasync.transport;

import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final List<FunctionWrapper> functions;
    private final List<Decoder<?, ?>> decoders;
    private final FunctionResolver resolver;
    private final Options options;
    private final RequestBuilder requestBuilder;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private STATUS status = Socket.STATUS.INIT;
    private final AtomicBoolean errorHandled = new AtomicBoolean();

    public WebSocketTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        super(new Builder());
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onThrowable(Throwable t) {
        status = Socket.STATUS.ERROR;
        errorHandled.set(TransportsUtil.invokeFunction(Event.ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (closed.getAndSet(true)) return;

        status = Socket.STATUS.CLOSE;

        TransportsUtil.invokeFunction(CLOSE, decoders, functions, String.class, CLOSE.name(), CLOSE.name(), resolver);

        if (webSocket != null && webSocket.isOpen())
            webSocket.close();
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

    @Override
    public void error(Throwable t) {
        logger.warn("", t);
        TransportsUtil.invokeFunction(Event.ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
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
        TransportsUtil.invokeFunction(HEADERS, decoders, functions, Map.class, headers.getHeaders(), HEADERS.name(), resolver);

        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket onCompleted() throws Exception {
        if (webSocket == null) {
            logger.error("WebSocket Handshake Failed");
            status = Socket.STATUS.ERROR;
            return null;
        }
        TransportsUtil.invokeFunction(TRANSPORT, decoders, functions, Request.TRANSPORT.class, name(), TRANSPORT.name(), resolver);
        return webSocket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSuccess(WebSocket webSocket) {
        this.webSocket = webSocket;

        ok.set(true);
        WebSocketTextListener l = new WebSocketTextListener() {
            @Override
            public void onMessage(String message) {
                message = message.trim();
                logger.debug("{} received {}", name(), message );
                if (message.length() > 0) {
                    TransportsUtil.invokeFunction(MESSAGE,
                            decoders,
                            functions,
                            message.getClass(),
                            message,
                            MESSAGE.name(),
                            resolver);
                }
            }

            @Override
            public void onFragment(String fragment, boolean last) {
            }

            @Override
            public void onOpen(WebSocket websocket) {
                // Could have been closed during the handshake.
                if (status.equals(Socket.STATUS.CLOSE)) return;

                closed.set(false);

                Event newStatus = status.equals(Socket.STATUS.INIT) ? OPEN : REOPENED;
                TransportsUtil.invokeFunction(newStatus,
                        decoders, functions, String.class, newStatus.name(), newStatus.name(), resolver);
            }

            @Override
            public void onClose(WebSocket websocket) {
                if (closed.get()) return;

                close();
                if (options.reconnect()) {
                    status = Socket.STATUS.REOPENED;
                    if (options.reconnectInSeconds() > 0) {
                        ScheduledExecutorService e = options.runtime().getConfig().reaper();
                        e.schedule(new Runnable() {
                            public void run() {
                                reconnect();
                            }
                        }, options.reconnectInSeconds(), TimeUnit.SECONDS);
                    } else {
                        reconnect();
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                status = Socket.STATUS.ERROR;

                errorHandled.set(TransportsUtil.invokeFunction(ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver));
            }
        };
        webSocket.addWebSocketListener(l);
        l.onOpen(webSocket);
    }

    void reconnect() {
        try {
            options.runtime().executeRequest(requestBuilder.build(), WebSocketTransport.this);
        } catch (IOException e) {
            logger.error("", e);
        }
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
        errorHandled.set(TransportsUtil.invokeFunction(ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver));
    }

    public WebSocket webSocket() {
        return webSocket;
    }
}
