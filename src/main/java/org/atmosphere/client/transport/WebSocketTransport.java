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
package org.atmosphere.client.transport;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.UpgradeHandler;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import org.atmosphere.client.Decoder;
import org.atmosphere.client.Function;
import org.atmosphere.client.FunctionWrapper;
import org.atmosphere.client.Future;
import org.atmosphere.client.Request;
import org.atmosphere.client.Socket;
import org.atmosphere.client.Transport;
import org.atmosphere.client.util.TypeResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketTransport extends WebSocketUpgradeHandler implements Transport {

    private WebSocket webSocket;
    private final AtomicBoolean ok = new AtomicBoolean(false);
    private Future f;
    private final List<FunctionWrapper> functions;

    private final Decoder<?> decoder;

    public WebSocketTransport(Decoder<?> decoder, List<FunctionWrapper> functions) {
        super(new Builder());
        if (decoder == null) {
            decoder = new Decoder<Object>() {
                @Override
                public Object decode(String s) {
                    return s;
                }
            };
        }
        this.decoder = decoder;
        this.functions = functions;
    }

    private void invokeFunction(Class<?> implementedType, Object instanceType, String functionName) {
        for (FunctionWrapper wrapper : functions) {
            Function f = wrapper.function();
            String fn = wrapper.functionName();
            Class<?>[] typeArguments = TypeResolver.resolveArguments(f.getClass(), Function.class);

            if (typeArguments.length > 0 && typeArguments[0].equals(implementedType)) {
                if (fn.isEmpty() || fn.equalsIgnoreCase(functionName)) {
                    f.on(instanceType);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onThrowable(Throwable t) {
        invokeFunction(t.getClass(), t, "error");
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
        invokeFunction(Integer.class, new Integer(responseStatus.getStatusCode()), "status");

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
        invokeFunction(Map.class, headers.getHeaders(), "headers");

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
                Object m = decoder.decode(message);
                invokeFunction(m.getClass(), m, "message");
            }

            @Override
            public void onFragment(String fragment, boolean last) {
            }

            @Override
            public void onOpen(WebSocket websocket) {
                invokeFunction(String.class, "Open", "open");
            }

            @Override
            public void onClose(WebSocket websocket) {
                invokeFunction(String.class, "Close", "close");
            }

            @Override
            public void onError(Throwable t) {
                invokeFunction(t.getClass(), t, "error");
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
    public Transport registerF(Function function) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void onFailure(Throwable t) {
        invokeFunction(t.getClass(), t, "error");
    }

}
