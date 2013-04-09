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
package org.atmosphere.wasync.transport;

import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.atmosphere.wasync.Socket.STATUS;

public class WebSocketTransport extends WebSocketUpgradeHandler implements Transport {

    private final Logger logger = LoggerFactory.getLogger(WebSocketTransport.class);
    private WebSocket webSocket;
    private final AtomicBoolean ok = new AtomicBoolean(false);
    private Future f;
    private final List<FunctionWrapper> functions;
    private final List<Decoder<?, ?>> decoders;
    private final FunctionResolver resolver;
    private final Options options;
    private final RequestBuilder requestBuilder;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private STATUS status = STATUS.INIT;

    public WebSocketTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        super(new Builder());
        this.decoders = request.decoders();

        if (decoders.size() == 0) {
            decoders.add(new Decoder<String, Object>() {
                @Override
                public Object decode(Transport.EVENT_TYPE e, String s) {
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
        TransportsUtil.invokeFunction(decoders, functions, t.getClass(), t, Function.MESSAGE.error.name(), resolver);
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) return;

        status = STATUS.CLOSE;

        TransportsUtil.invokeFunction(EVENT_TYPE.CLOSE, decoders, functions, String.class, Function.MESSAGE.close.name(), Function.MESSAGE.close.name(), resolver);

        if (webSocket != null)
            webSocket.close();
    }

    @Override
    public STATUS status() {
        return status;
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
        TransportsUtil.invokeFunction(decoders, functions, Integer.class, new Integer(responseStatus.getStatusCode()), Function.MESSAGE.status.name(), resolver);
        if (responseStatus.getStatusCode() == 101) {
            return STATE.UPGRADE;
        } else {
            return STATE.ABORT;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
        TransportsUtil.invokeFunction(decoders, functions, Map.class, headers.getHeaders(), Function.MESSAGE.headers.name(), resolver);

        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket onCompleted() throws Exception {
        if (webSocket == null) {
            status = STATUS.ERROR;
            throw new IllegalStateException("WebSocket is null");
        }
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
                if (message.length() > 0) {
                    TransportsUtil.invokeFunction(EVENT_TYPE.MESSAGE,
                            decoders,
                            functions,
                            message.getClass(),
                            message,
                            Function.MESSAGE.message.name(),
                            resolver);
                }
            }

            @Override
            public void onFragment(String fragment, boolean last) {
            }

            @Override
            public void onOpen(WebSocket websocket) {
                // Could have been closed during the handshake.
                if (status.equals(STATUS.CLOSE)) return;

                boolean reconnect = false;
                if (!status.equals(STATUS.INIT)) {
                    reconnect = true;
                }

                status = STATUS.OPEN;

                TransportsUtil.invokeFunction(reconnect ? EVENT_TYPE.RECONNECT : EVENT_TYPE.OPEN,
                        decoders, functions, String.class, Function.MESSAGE.open.name(), Function.MESSAGE.open.name(), resolver);
            }

            @Override
            public void onClose(WebSocket websocket) {
                if (closed.get()) return;

                status = STATUS.CLOSE;

                TransportsUtil.invokeFunction(EVENT_TYPE.CLOSE, decoders, functions, String.class, Function.MESSAGE.close.name(), Function.MESSAGE.close.name(), resolver);
                if (options.reconnect()) {
                    ScheduledExecutorService e = options.runtime().getConfig().reaper();
                    e.schedule(new Runnable() {
                        public void run() {
                            try {
                                options.runtime().executeRequest(requestBuilder.build(), WebSocketTransport.this);
                            } catch (IOException e) {
                                logger.error("", e);
                            }
                        }
                    }, options.reconnectInSeconds(), TimeUnit.SECONDS);

                }
            }

            @Override
            public void onError(Throwable t) {
                status = STATUS.CLOSE;

                TransportsUtil.invokeFunction(decoders, functions, t.getClass(), t, Function.MESSAGE.error.name(), resolver);
            }
        };
        webSocket.addWebSocketListener(l);
        l.onOpen(webSocket);
    }

    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.WEBSOCKET;
    }

    @Override
    public Transport future(Future f) {
        this.f = f;
        return this;
    }

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
        TransportsUtil.invokeFunction(decoders, functions, t.getClass(), t, Function.MESSAGE.error.name(), resolver);
    }

}
