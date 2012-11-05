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
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Request;

import java.util.List;
import java.util.Map;

public class StreamTransport<T> implements AsyncHandler<String>, Transport {
    private final static String DEFAULT_CHARSET = "ISO-8859-1";

    protected Future f;
    protected final List<FunctionWrapper> functions;
    private final List<Decoder<? extends Object, ?>> decoders;
    //TODO fix me
    protected String charSet = DEFAULT_CHARSET;
    private final FunctionResolver resolver;


    public StreamTransport(List<Decoder<? extends Object, ?>> decoders, List<FunctionWrapper> functions, FunctionResolver resolver) {
        if (decoders.size() == 0) {
            decoders.add(new Decoder<String, Object>() {
                @Override
                public Object decode(String s) {
                    return s;
                }
            });
        }
        this.decoders = decoders;
        this.functions = functions;
        this.resolver = resolver;
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
        String m = new String(bodyPart.getBodyPartBytes(), charSet).trim();
        if (!m.isEmpty()) {
            TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, Function.MESSAGE.message.name(), resolver);
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
        TransportsUtil.invokeFunction(decoders, functions, String.class, "Open", Function.MESSAGE.open.name(), resolver);
        TransportsUtil.invokeFunction(decoders, functions, Integer.class, new Integer(responseStatus.getStatusCode()), Function.MESSAGE.status.name(), resolver);

        return STATE.CONTINUE;
    }

    @Override
    public String onCompleted() throws Exception {
        return "";
    }

    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.STREAMING;
    }

    @Override
    public void close() {
        TransportsUtil.invokeFunction(decoders, functions, String.class, "Close", Function.MESSAGE.open.name(), resolver);
    }
}

