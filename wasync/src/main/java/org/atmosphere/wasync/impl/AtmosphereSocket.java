/*
 * Copyright 2017 Async-IO.org
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
    private AtomicBoolean closedByProtocol = new AtomicBoolean();

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

        ((DefaultOptions) options).b.reconnect(false);

        List<String> uuid = decodeQueryString(request).get("X-Atmosphere-tracking-id");
        if (!closedByProtocol.getAndSet(true)) {
            RequestBuilder r = new RequestBuilder();
            FluentStringsMap f = new FluentStringsMap();
            f.add("X-Atmosphere-Transport", "close").add("X-Atmosphere-tracking-id", uuid);

            r.setUrl(request.uri())
                    .setMethod("GET")
                    .setHeaders(request.headers())
                    .setQueryParams(f);
            try {
                options.runtime().prepareRequest(r.build()).execute().get();
            } catch (Exception e) {
                logger.debug("Was unable to send the close request {}", uuid);
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
        if(request != null) {
            doCloseRequest();
        }

        // Not connected, but close the underlying AHC.
        if (transportInUse == null) {
            super.closeRuntime(false);
        } else if (socketRuntime != null && (closedByProtocol.get() || !transportInUse.status().equals(STATUS.CLOSE))) {
            transportInUse.close();
            super.closeRuntime(true);
        }
    }
}
