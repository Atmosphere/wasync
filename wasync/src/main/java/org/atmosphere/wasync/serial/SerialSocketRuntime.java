/*
 * Copyright 2008-2025 Async-IO.org
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;

import org.asynchttpclient.Response;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.impl.DefaultFuture;
import org.atmosphere.wasync.impl.SocketRuntime;
import org.atmosphere.wasync.transport.WebSocketTransport;
import org.atmosphere.wasync.util.FutureProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;

/**
 * Serial extension for the {@link SocketRuntime}
 *
 * @author Jeanfrancois Arcand
 */
public class SerialSocketRuntime extends SocketRuntime {

    private final static Logger logger = LoggerFactory.getLogger(SerialSocketRuntime.class);
    private final SerializedSocket serializedSocket;

    public SerialSocketRuntime(Transport transport, Options options, DefaultFuture rootFuture, SerializedSocket serializedSocket, List<FunctionWrapper> functions) {
        super(transport, options, rootFuture, functions);
        this.serializedSocket = serializedSocket;
    }

    @Override
    public Future write(Request request, Object data) throws IOException {

        if (WebSocketTransport.class.isAssignableFrom(transport.getClass())) {
            Object object = invokeEncoder(request.encoders(), data);
            rootFuture.done();
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

            FutureProxy<?> f;
            if (serializedSocket.getSerializedFireStage() != null) {
                final SettableFuture<Response> future = SettableFuture.create();
                serializedSocket.getSerializedFireStage().enqueue(encodedPayload, future);
                f = new FutureProxy(serializedSocket, future);
            } else {
                f = new FutureProxy(serializedSocket, serializedSocket.directWrite(encodedPayload));
            }
            transport.future(f);
            return f;
        }
        return rootFuture.finishOrThrowException();
    }
}