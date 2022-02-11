/*
 * Copyright 2008-2022 Async-IO.org
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

import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.RequestBuilder;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;

import io.netty.handler.codec.http.HttpHeaders;

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
    public State onHeadersReceived(HttpHeaders headers) throws Exception {

        String ct = headers.get("Content-Type");
        if (ct == null  || ct.length() == 0 || !ct.contains("text/event-stream")) {
            status = Socket.STATUS.ERROR;
            throw new TransportNotSupported(500, "Invalid Content-Type" + ct);
        }

        return super.onHeadersReceived(headers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
    	if(!bodyPart.isLast()) {
    		String m = new String(bodyPart.getBodyPartBytes(), charSet).trim();
        	if (m.length() > 0) {
            	String[] data = m.split("data:");
            	for (String d : data) {
                	if (d.length() > 0)
                    	TransportsUtil.invokeFunction(decoders, functions, d.getClass(), d, MESSAGE.name(), resolver);
                	unlockFuture();
            	}
        	}
    	}
        return State.CONTINUE;
    }
}
