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

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.FluentStringsMap;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.RequestBuilder;
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

public class StreamTransport<T> implements AsyncHandler<String>, Transport {
    private final static String DEFAULT_CHARSET = "UTF-8";
    private final Logger logger = LoggerFactory.getLogger(StreamTransport.class);

    protected Future f;
    protected final List<FunctionWrapper> functions;
    private final List<Decoder<? extends Object, ?>> decoders;
    //TODO fix me
    protected String charSet = DEFAULT_CHARSET;
    private final FunctionResolver resolver;
    private final Options options;
    private final RequestBuilder requestBuilder;
    private final Request request;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public StreamTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
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
        this.request = request;
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
    public void onThrowable(Throwable t) {
        TransportsUtil.invokeFunction(decoders, functions, t.getClass(), t, Function.MESSAGE.error.name(), resolver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
    	if(request.headers().get("Content-Type").contains("application/octet-stream")) {
    		byte[] payload = bodyPart.getBodyPartBytes();
    		TransportsUtil.invokeFunction(decoders, functions, payload.getClass(), payload, Function.MESSAGE.message.name(), resolver);
    	} else {
    		String m = new String(bodyPart.getBodyPartBytes(), charSet).trim();
    		if (!m.isEmpty()) {
    			TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, Function.MESSAGE.message.name(), resolver);
    		}
    	}
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
        TransportsUtil.invokeFunction(decoders, functions, Map.class, headers.getHeaders(), Function.MESSAGE.headers.name(), resolver);

        // TODO: Parse charset
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        f.done();
        TransportsUtil.invokeFunction(EVENT_TYPE.OPEN, decoders, functions, String.class, Function.MESSAGE.open.name(), Function.MESSAGE.open.name(), resolver);
        TransportsUtil.invokeFunction(EVENT_TYPE.MESSAGE, decoders, functions, Integer.class, new Integer(responseStatus.getStatusCode()), Function.MESSAGE.status.name(), resolver);

        return STATE.CONTINUE;
    }

    @Override
    public String onCompleted() throws Exception {
        if (closed.get()) return "";

        if (options.reconnect()) {
            ScheduledExecutorService e = options.runtime().getConfig().reaper();
            e.schedule(new Runnable() {
                public void run() {

                    Map<String, List<String>> c = request.queryString();
                    FluentStringsMap f = new FluentStringsMap();
                    f.putAll(c);
                    try {
                        options.runtime().executeRequest(requestBuilder.setQueryParameters(f).build(), StreamTransport.this);
                    } catch (IOException e) {
                        logger.error("", e);
                    }
                }
            }, options.reconnectInSeconds(), TimeUnit.SECONDS);
        }
        return "";
    }

    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.STREAMING;
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) return;

        TransportsUtil.invokeFunction(decoders, functions, String.class, Function.MESSAGE.close.name(), Function.MESSAGE.close.name(), resolver);
    }
}

