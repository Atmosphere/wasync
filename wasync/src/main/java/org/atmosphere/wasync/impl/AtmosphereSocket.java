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
package org.atmosphere.wasync.impl;

import com.ning.http.client.FluentStringsMap;
import com.ning.http.client.RequestBuilder;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.transport.WebSocketTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AtmosphereSocket extends DefaultSocket {

    private final static Logger logger = LoggerFactory.getLogger(AtmosphereSocket.class);
    private AtomicBoolean closed = new AtomicBoolean();

    public AtmosphereSocket(Options options) {
        super(options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketRuntime createRuntime() {
        return new AtmosphereSocketRuntime(options, new DefaultFuture(this), functions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketRuntime createRuntime(Options options, List<FunctionWrapper> functions) {
        return new AtmosphereSocketRuntime(WebSocketTransport.class.cast(transportInUse), options, socketRuntime.future(), functions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void closeRuntime(boolean async) {
        doCloseRequest();
        super.closeRuntime(async);
    }

    protected void doCloseRequest() {

        ((DefaultOptions)options).b.reconnect(false);

        if (!closed.getAndSet(true)) {
            RequestBuilder r = new RequestBuilder();
            FluentStringsMap f = new FluentStringsMap();
            f.add("X-Atmosphere-Transport", "close").add("X-Atmosphere-tracking-id", decodeQueryString(request).get("X-Atmosphere-tracking-id"));

            r.setUrl(request.uri())
                    .setMethod("GET")
                    .setQueryParameters(f);
            try {
                options.runtime().prepareRequest(r.build()).execute().get();
            } catch (Exception e) {
                logger.trace("", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        doCloseRequest();
        super.close();
    }
}
