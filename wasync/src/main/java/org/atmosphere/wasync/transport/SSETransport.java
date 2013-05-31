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

import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.RequestBuilder;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;

import java.util.List;
import java.util.Map;

import static org.atmosphere.wasync.Event.MESSAGE;

/**
 * Server Side Events {@link org.atmosphere.wasync.Transport} implementation
 *
 * @author Jeanfrancois Arcand
 */
public class SSETransport extends StreamTransport {

    public SSETransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        super(requestBuilder, options, request, functions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.SSE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {

        List<String> ct = headers.getHeaders().get("Content-Type");
        if ( ct == null || ct.size() == 0 || !ct.get(0).contains("text/event-stream")) {
            status = Socket.STATUS.ERROR;
            throw new TransportNotSupported(500, "Invalid Content-Type" + ct);
        }

        TransportsUtil.invokeFunction(decoders, functions, Map.class, headers.getHeaders(), MESSAGE.name(), resolver);

        // TODO: Parse charset
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
        String m = new String(bodyPart.getBodyPartBytes(), charSet).trim();
        if (!m.isEmpty()) {
            String[] data = m.split("data:");
            for (String d: data) {
                if (!d.isEmpty())
                    TransportsUtil.invokeFunction(decoders, functions, d.getClass(), d, MESSAGE.name(), resolver);
            }

            if (data.length == 0) {
                return STATE.ABORT;
            }
        }
        return STATE.CONTINUE;
    }
}
