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
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.transport.TransportNotSupported;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    protected void addFunction(final long timeout, final TimeUnit tu) {
        functions.add(new FunctionWrapper("", new Function<TransportNotSupported>() {
            @Override
            public void on(TransportNotSupported transportNotSupported) {
                request.transport().remove(0);
                if (request.transport().size() > 0) {
                    try {
                        if (request.queryString().get("X-Atmosphere-Transport") != null) {
                            Request.TRANSPORT rt = request.transport().get(0);
                            String t = rt == Request.TRANSPORT.LONG_POLLING ? "long-polling" : rt.name();
                            request.queryString().put("X-Atmosphere-Transport", Arrays.asList(new String[]{t}));
                        }
                        open(request, timeout, tu);
                    } catch (IOException e) {
                        logger.error("", e);
                    }
                } else {
                    throw new Error("No suitable transport supported by the server");
                }
            }
        }));
    }

    /**
     * {@inheritDoc}
     */
    public SocketRuntime createRuntime(DefaultFuture future, Options options, List<FunctionWrapper> functions) {
        return new AtmosphereSocketRuntime(transportInUse, options, future, functions);
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
