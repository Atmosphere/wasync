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
package org.atmosphere.wasync.serial;

import com.ning.http.client.AsyncHttpClient;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.AtmosphereRequest;
import org.atmosphere.wasync.impl.ClientUtil;
import org.atmosphere.wasync.impl.DefaultRequestBuilder;
import org.atmosphere.wasync.impl.AtmosphereRequest.AtmosphereRequestBuilder;

/**
 * {@code SerializedClient} is a {@link org.atmosphere.wasync.Client} that guarantees ordered message delivery, in-line with the
 * {@link Socket#fire(Object)} invocation sequence.
 * <p/>
 * A sequence of fire calls over a {@code SerializedClient}'s socket (created through {@link SerializedClient#create()} :
 * <blockquote><pre>
 *     socket.fire("message1").fire("message2");
 * </pre></blockquote>
 * guarantees that {@code message1} arrives at the recipient-side before {@code message2}. By default, wAsync uses multiple underlying
 * connections in delivering fire payloads. The {@code SerializedClient} guarantees that only one connection is used at any moment
 * in time, while still providing an asynchronous fire interface to clients.
 * <p/>
 * {@code SerializedClient} instances can be configured by means of a {@link SerializedFireStage} in deciding on the exact
 * staging semantics and the (non-functional) quality properties of a supporting stage. The default implementation provided is
 * {@link DefaultSerializedFireStage}.
 * <p/>
 *
 * @author Christian Bach
 */
public class SerializedClient implements Client<SerializedOptions, SerializedOptionsBuilder, AtmosphereRequest.AtmosphereRequestBuilder> {

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket create(SerializedOptions options) {
        AsyncHttpClient asyncHttpClient = options.runtime();
        if (asyncHttpClient == null || asyncHttpClient.isClosed()) {
            asyncHttpClient = ClientUtil.createDefaultAsyncHttpClient(options);
            options.runtime(asyncHttpClient);
        }
        return new SerializedSocket(options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket create() {
        AsyncHttpClient asyncHttpClient = ClientUtil.createDefaultAsyncHttpClient(newOptionsBuilder().reconnect(true).build());

        return new SerializedSocket(
                new SerializedOptionsBuilder()
                        .runtime(asyncHttpClient)
                        .serializedFireStage(new DefaultSerializedFireStage())
                        .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SerializedOptionsBuilder newOptionsBuilder() {
        return new SerializedOptionsBuilder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AtmosphereRequestBuilder newRequestBuilder() {
        AtmosphereRequestBuilder b = new AtmosphereRequestBuilder();
        return AtmosphereRequestBuilder.class.cast(b.resolver(FunctionResolver.DEFAULT));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AtmosphereRequestBuilder newRequestBuilder(Class<AtmosphereRequest.AtmosphereRequestBuilder> clazz) {
        AtmosphereRequestBuilder b;
        try {
            b = clazz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return AtmosphereRequestBuilder.class.cast(b.resolver(FunctionResolver.DEFAULT));
    }
}
