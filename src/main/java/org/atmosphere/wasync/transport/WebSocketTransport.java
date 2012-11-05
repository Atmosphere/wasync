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
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Transport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketTransport extends WebSocketUpgradeHandler implements Transport {

    private WebSocket webSocket;
    private final AtomicBoolean ok = new AtomicBoolean(false);
    private Future f;
    private final List<FunctionWrapper> functions;
    private final List<Decoder<?, ?>> decoders;
    private final FunctionResolver resolver;

    public WebSocketTransport(List<Decoder<?, ?>> decoders, List<FunctionWrapper> functions, FunctionResolver resolver) {
        super(new Builder());
        if (decoders.size() == 0) {
            decoders.add(new Decoder<String, Object>() {
                @Override
                public Object decode(String s) {
                    return s;
                }
            });
        }
        this.decoders = decoders;
        this.functions = functions;
        this.resolver = resolver;
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
                TransportsUtil.invokeFunction(decoders, functions, message.getClass(), message, Function.MESSAGE.message.name(), resolver);
            }

            @Override
            public void onFragment(String fragment, boolean last) {
            }

            @Override
            public void onOpen(WebSocket websocket) {
                TransportsUtil.invokeFunction(decoders, functions, String.class, "Open", Function.MESSAGE.open.name(), resolver);
            }

            @Override
            public void onClose(WebSocket websocket) {
                TransportsUtil.invokeFunction(decoders, functions, String.class, "Close", Function.MESSAGE.close.name(), resolver);
            }

            @Override
            public void onError(Throwable t) {
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
