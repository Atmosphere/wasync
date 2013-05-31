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
package org.atmosphere.wasync.transport;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.FluentStringsMap;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.RequestBuilder;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.atmosphere.wasync.Event.CLOSE;
import static org.atmosphere.wasync.Event.ERROR;
import static org.atmosphere.wasync.Event.HEADERS;
import static org.atmosphere.wasync.Event.MESSAGE;
import static org.atmosphere.wasync.Event.OPEN;
import static org.atmosphere.wasync.Event.REOPENED;
import static org.atmosphere.wasync.Event.STATUS;
import static org.atmosphere.wasync.Event.TRANSPORT;
import static org.atmosphere.wasync.Socket.STATUS;

/**
 * Streaming {@link org.atmosphere.wasync.Transport} implementation
 *
 * @author Jeanfrancois Arcand
 */
public class StreamTransport implements AsyncHandler<String>, Transport {
    private final static String DEFAULT_CHARSET = "UTF-8";
    private final Logger logger = LoggerFactory.getLogger(StreamTransport.class);

    protected final List<FunctionWrapper> functions;
    protected final List<Decoder<? extends Object, ?>> decoders;
    //TODO fix me
    protected String charSet = DEFAULT_CHARSET;
    protected final FunctionResolver resolver;
    protected final Options options;
    protected final RequestBuilder requestBuilder;
    protected final Request request;
    protected final AtomicBoolean closed = new AtomicBoolean(false);
    protected final boolean isBinary;
    protected STATUS status =  Socket.STATUS.INIT;
    protected final AtomicBoolean errorHandled = new AtomicBoolean();
    
    private boolean protocolReceived = false;

    public StreamTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        this.decoders = request.decoders();

        if (decoders.size() == 0) {
            decoders.add(new Decoder<String, Object>() {
                @Override
                public Object decode(Event e, String s) {
                    return s;
                }
            });
        }
        this.functions = functions;
        this.resolver = request.functionResolver();
        this.options = options;
        this.requestBuilder = requestBuilder;
        this.request = request;

        isBinary = request.headers().get("Content-Type") != null ?
                request.headers().get("Content-Type").contains("application/octet-stream") : false;
    }

    /**
     * {@inheritDoc}
     */
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
        status = Socket.STATUS.ERROR;
        errorHandled.set(TransportsUtil.invokeFunction(ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
        if (isBinary) {
            byte[] payload = bodyPart.getBodyPartBytes();
			if (payload[0] == 0x20) {
				if (!protocolReceived) {
					String m = new String(bodyPart.getBodyPartBytes(), charSet).trim();
		            if (!m.isEmpty()) {
		            	// need to forward this chunk, else the AtmosphereRequest decoder for the initial handshake is not successful.
		            	TransportsUtil.invokeFunction(decoders, functions, payload.getClass(), payload, MESSAGE.name(), resolver);
		            	protocolReceived = true;
		            }
				}
			} else {
				TransportsUtil.invokeFunction(decoders, functions, payload.getClass(), payload, MESSAGE.name(), resolver);
			}
        } else {
            String m = new String(bodyPart.getBodyPartBytes(), charSet).trim();
            if (!m.isEmpty()) {
                TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, MESSAGE.name(), resolver);
            }
        }
        return AsyncHandler.STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
        TransportsUtil.invokeFunction(HEADERS, decoders, functions, Map.class, headers.getHeaders(), HEADERS.name(), resolver);

        // TODO: Parse charset
        return AsyncHandler.STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncHandler.STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        TransportsUtil.invokeFunction(TRANSPORT, decoders, functions, Request.TRANSPORT.class, name(), TRANSPORT.name(), resolver);

        errorHandled.set(false);
        closed.set(false);

        Event newStatus = status.equals(Socket.STATUS.INIT) ? OPEN : REOPENED;
        TransportsUtil.invokeFunction(newStatus,
                decoders, functions, String.class, newStatus.name(), newStatus.name(), resolver);

        TransportsUtil.invokeFunction(MESSAGE, decoders, functions, Integer.class, new Integer(responseStatus.getStatusCode()), STATUS.name(), resolver);

        return AsyncHandler.STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String onCompleted() throws Exception {
        if (closed.get()) return "";

        if (status == Socket.STATUS.ERROR) {
            return "";
        }

        close();

        if (options.reconnect()) {
            // We can't let the STATUS to close as fire() method won't work.
            status = Socket.STATUS.REOPENED;
            if (options.reconnectInSeconds() > 0) {
                ScheduledExecutorService e = options.runtime().getConfig().reaper();
                e.schedule(new Runnable() {
                    public void run() {
                        reconnect();
                    }
                }, options.reconnectInSeconds(), TimeUnit.SECONDS);
            } else {
                reconnect();
            }
        }
        return "";
    }

    void reconnect() {
        Map<String, List<String>> c = request.queryString();
        FluentStringsMap f = new FluentStringsMap();
        f.putAll(c);
        try {
            options.runtime().executeRequest(requestBuilder.setQueryParameters(f).build(), StreamTransport.this);
        } catch (IOException e) {
            logger.error("", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.STREAMING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (closed.getAndSet(true)) return;
        status = Socket.STATUS.CLOSE;

        TransportsUtil.invokeFunction(CLOSE, decoders, functions, String.class, CLOSE.name(), CLOSE.name(), resolver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATUS status() {
        return status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean errorHandled() {
        return errorHandled.get();
    }

    @Override
    public void error(Throwable t) {
        logger.warn("", t);
        TransportsUtil.invokeFunction(ERROR, decoders, functions, t.getClass(), t, ERROR.name(), resolver);
    }
}

