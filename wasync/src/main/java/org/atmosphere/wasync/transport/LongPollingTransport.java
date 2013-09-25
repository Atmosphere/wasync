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
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.RequestBuilder;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.atmosphere.wasync.Event.MESSAGE;

/**
 * Long-Polling {@link org.atmosphere.wasync.Transport} implementation
 *
 * @author Jeanfrancois Arcand
 */
public class LongPollingTransport extends StreamTransport {

    /**
     * When Atmosphere Protocol is used, we must not invoke any Function until the protocol has been processed.
     */
    private final AtomicBoolean handshakeOccured = new AtomicBoolean(true);
    protected boolean protocolReceived = false;

    public LongPollingTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        super(requestBuilder, options, request, functions);
        List<String> protocol = request.queryString().get("X-atmo-protocol");
        List<String> transport = request.queryString().get("X-Atmosphere-Transport");
        if (protocol != null && transport != null
                && protocol.get(0).equals("true")
                && transport.get(0).equals("long-polling")) {
            handshakeOccured.set(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
        if (handshakeOccured.get()) {
            return super.onHeadersReceived(headers);
        }
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncHandler.STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        if (handshakeOccured.get()) {
            return super.onStatusReceived(responseStatus);
        }
        return STATE.CONTINUE;
    }

    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
        handshakeOccured.set(true);
        if (isBinary) {
            byte[] payload = bodyPart.getBodyPartBytes();
            if (protocolEnabled && !protocolReceived) {
                if (!whiteSpace(payload)) {
                    TransportsUtil.invokeFunction(decoders, functions, payload.getClass(), payload, MESSAGE.name(), resolver);
                    protocolReceived = true;
                }
                return AsyncHandler.STATE.CONTINUE;
            } else if (!whiteSpace(payload)) {
                TransportsUtil.invokeFunction(decoders, functions, payload.getClass(), payload, MESSAGE.name(), resolver);
            }
            unlockFuture();
        } else {
            String m = new String(bodyPart.getBodyPartBytes(), charSet).trim();
            if (protocolEnabled && !protocolReceived) {
                if (m.length() > 0) {
                    TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, MESSAGE.name(), resolver);
                    protocolReceived = true;
                }
                return AsyncHandler.STATE.CONTINUE;
            } else if (m.length() > 0) {
                TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, MESSAGE.name(), resolver);
            }
            unlockFuture();
        }
        return AsyncHandler.STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.LONG_POLLING;
    }

}

