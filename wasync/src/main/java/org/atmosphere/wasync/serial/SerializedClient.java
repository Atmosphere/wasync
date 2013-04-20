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
import org.atmosphere.wasync.impl.ClientUtil;
import org.atmosphere.wasync.impl.DefaultRequestBuilder;

/**
 * A {@link org.atmosphere.wasync.Client} that guarantee message delivery order when {@link Socket#fire(Object)} is invoked. Doing:
 * <blockquote><pre>
 * <p/>
 *     socket.fire("message1").fire("message2");
 * </pre></blockquote>
 * means message1 will be send, then message2. By default, wAsync is asynchronous so if you need order use the {@link SerializedClient}
 *
 * @author Christian Bach
 */
public class SerializedClient implements Client<SerializedOptions, SerializedOptionsBuilder, RequestBuilder> {

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

        return new SerializedSocket(new SerializedOptionsBuilder().runtime(asyncHttpClient).build());
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
    public RequestBuilder newRequestBuilder() {
        RequestBuilder b = new DefaultRequestBuilder();
        return b.resolver(FunctionResolver.DEFAULT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RequestBuilder newRequestBuilder(Class<RequestBuilder> clazz) {
        RequestBuilder b = null;
        try {
            b = clazz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return b.resolver(FunctionResolver.DEFAULT);
    }
}
