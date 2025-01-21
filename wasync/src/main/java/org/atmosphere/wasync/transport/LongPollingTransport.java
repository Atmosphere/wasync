/*
 * Copyright 2008-2025 Async-IO.org
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

import static org.atmosphere.wasync.Event.MESSAGE;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.RequestBuilder;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.util.Utils;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * Long-Polling {@link org.atmosphere.wasync.Transport} implementation
 *
 * @author Jeanfrancois Arcand
 */
public class LongPollingTransport extends StreamTransport {

    /**
     * When Atmosphere Protocol is used, we must not invoke any Function until the protocol has been processed.
     */
    private final AtomicBoolean handshakeOccurred = new AtomicBoolean(true);
    private int count = 0;

    public LongPollingTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        super(requestBuilder, options, request, functions);
        List<String> protocol = request.queryString().get("X-atmo-protocol");
        List<String> transport = request.queryString().get("X-Atmosphere-Transport");
        if (protocol != null && transport != null
                && protocol.get(0).equals("true")
                && transport.get(0).equals("long-polling")) {
            handshakeOccurred.set(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State onHeadersReceived(HttpHeaders headers) throws Exception {
        if (handshakeOccurred.get()) {
            return super.onHeadersReceived(headers);
        }
        return State.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        if (handshakeOccurred.get()) {
            // onOpen only called once
            if (protocolEnabled && ++count == 1) {
                status = Socket.STATUS.INIT;
            }
            return super.onStatusReceived(responseStatus);
        }
        return State.CONTINUE;
    }

    @Override
    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
        handshakeOccurred.set(true);
        if (isBinary) {
            byte[] payload = bodyPart.getBodyPartBytes();
            if (protocolEnabled && !protocolReceived) {
                if (!Utils.whiteSpace(payload)) {
                    TransportsUtil.invokeFunction(decoders, functions, payload.getClass(), payload, MESSAGE.name(), resolver);
                    protocolReceived = true;
                }
                return AsyncHandler.State.CONTINUE;
            } else {
            	if(!bodyPart.isLast())
            		TransportsUtil.invokeFunction(decoders, functions, payload.getClass(), payload, MESSAGE.name(), resolver);
            }
            unlockFuture();
        } else {
            String m = new String(bodyPart.getBodyPartBytes(), charSet);
            if (protocolEnabled && !protocolReceived) {
                m = m.trim();
                if (m.length() > 0) {
                    TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, MESSAGE.name(), resolver);
                    protocolReceived = true;
                }
                return AsyncHandler.State.CONTINUE;
            } else {
            	if(!bodyPart.isLast())
            		TransportsUtil.invokeFunction(decoders, functions, m.getClass(), m, MESSAGE.name(), resolver);
            }
            unlockFuture();
        }
        return AsyncHandler.State.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.LONG_POLLING;
    }

}

