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
package org.atmosphere.client.transport;


import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import org.atmosphere.client.Decoder;
import org.atmosphere.client.Function;
import org.atmosphere.client.FunctionWrapper;
import org.atmosphere.client.Future;
import org.atmosphere.client.Request;
import org.atmosphere.client.Transport;

import java.util.List;
import java.util.Map;

public class StreamTransport<T> implements AsyncHandler<String>,Transport {
    private final static String DEFAULT_CHARSET = "ISO-8859-1";

    protected Future f;
    protected final List<FunctionWrapper> functions;
    private final List<Decoder<? extends Object,?>> decoders;
    //TODO fix me
    protected String charSet = DEFAULT_CHARSET;

    public StreamTransport(List<Decoder<? extends Object,?>>decoders, List<FunctionWrapper> functions) {
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
        t.printStackTrace();
        TransportsUtil.invokeFunction(decoders, functions, t.getClass(), t, Function.MESSAGE.error.name());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
        String m = new String(bodyPart.getBodyPartBytes(), charSet).trim();
        if (!m.isEmpty()) {
            TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, Function.MESSAGE.message.name());
        }
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
        TransportsUtil.invokeFunction(decoders, functions, Map.class, headers.getHeaders(), Function.MESSAGE.headers.name());

        // TODO: Parse charset
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        f.done();
        TransportsUtil.invokeFunction(decoders, functions, String.class, "Open", Function.MESSAGE.open.name());
        TransportsUtil.invokeFunction(decoders, functions, Integer.class, new Integer(responseStatus.getStatusCode()), Function.MESSAGE.status.name());

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
        TransportsUtil.invokeFunction(decoders, functions, String.class, "Close", Function.MESSAGE.open.name());
    }
}

