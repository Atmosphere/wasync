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
package org.atmosphere.client;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.websocket.WebSocket;
import org.atmosphere.client.util.ReaderInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SocketImpl implements Socket {

    private final Request request;
    private final ConcurrentHashMap<EVENT, Function> functions = new ConcurrentHashMap<EVENT, Function>();
    private final InternalSocket socket;

    public SocketImpl(Request request, AsyncHttpClient asyncHttpClient, Transport transport) {
        this.request = request;
        this.socket = new InternalSocket(asyncHttpClient);
    }

    public SocketImpl(Request request, WebSocket webSocket, Transport transport) {
        this.request = request;
        this.socket = new InternalSocket(webSocket);
    }

    public Future send(Object data) throws IOException {
        socket.write(request, data);
        return new Future(this);
    }

    public Socket on(EVENT type, Function<?> function) {
        functions.put(type, function);
        return this;
    }

    private final static class InternalSocket {

        private final WebSocket webSocket;
        private final AsyncHttpClient asyncHttpClient;

        public InternalSocket(WebSocket webSocket) {
            this.webSocket = webSocket;
            this.asyncHttpClient = null;
        }

        public InternalSocket(AsyncHttpClient asyncHttpClient) {
            this.webSocket = null;
            this.asyncHttpClient = asyncHttpClient;
        }

        public InternalSocket write(Request request, Object data) throws IOException {

            // Execute encoder
            Encoder.EncodedMessage<?> em = executeEncoders(request.encoders(), data);
            if (webSocket != null) {
                if (InputStream.class.isAssignableFrom(em.encoding())) {
                    InputStream is = (InputStream) em.message();
                    ByteArrayOutputStream bs = new ByteArrayOutputStream();
                    //TODO: We need to stream directly, in AHC!
                    byte[] buffer = new byte[8192];
                    int n = 0;
                    while (-1 != (n = is.read(buffer))) {
                        bs.write(buffer, 0, n);
                    }
                    webSocket.sendMessage(bs.toByteArray());
                } else if (Reader.class.isAssignableFrom(em.encoding())) {
                    Reader is = (Reader) em.message();
                    StringWriter bs = new StringWriter();
                    //TODO: We need to stream directly, in AHC!
                    char[] chars = new char[8192];
                    int n = 0;
                    while (-1 != (n = is.read(chars))) {
                        bs.write(chars, 0, n);
                    }
                    webSocket.sendTextMessage(bs.getBuffer().toString());
                } else if (String.class.isAssignableFrom(em.encoding())) {
                    webSocket.sendTextMessage(em.message().toString());
                } else if (byte[].class.isAssignableFrom(em.encoding())) {
                    webSocket.sendMessage((byte[]) em.message());
                } else {
                    throw new IllegalStateException("No Encoder for " + data);
                }
            } else {
                if (InputStream.class.isAssignableFrom(em.encoding())) {
                    //TODO: Allow reading the response.
                    asyncHttpClient.preparePost(request.uri().toURL().toString())
                            .setBody((InputStream) em.message()).execute();
                } else if (Reader.class.isAssignableFrom(em.encoding())) {
                    asyncHttpClient.preparePost(request.uri().toURL().toString())
                            .setBody(new ReaderInputStream((Reader) em.message())).execute();
                    return this;
                } else if (String.class.isAssignableFrom(em.encoding())) {
                    asyncHttpClient.preparePost(request.uri().toURL().toString()).setBody((String) em.message()).execute();
                } else if (byte[].class.isAssignableFrom(em.encoding())) {
                    asyncHttpClient.preparePost(request.uri().toURL().toString()).setBody((byte[]) em.message()).execute();
                } else {
                    throw new IllegalStateException("No Encoder for " + data);
                }
            }
            return this;
        }


        private Encoder.EncodedMessage<?> executeEncoders(List<Encoder> encoders, Object data) {
            // TODO This won't work, we need the type.
            Encoder.EncodedMessage em = new Encoder.EncodedMessage(data.getClass(), data);

            for (Encoder<?, ?> e : encoders) {
                em = e.encode(em);
            }
            return em;
        }

    }


}
