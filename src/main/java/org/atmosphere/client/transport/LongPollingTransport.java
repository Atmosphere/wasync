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


import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import org.atmosphere.client.Decoder;
import org.atmosphere.client.FunctionWrapper;
import org.atmosphere.client.Request;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class LongPollingTransport<T> extends StreamTransport {

    private final Request request;
    private final AsyncHttpClient asyncHttpClient;
    private final AtomicBoolean hasSucceedOnce = new AtomicBoolean(false);

    public LongPollingTransport(Decoder<?> decoder, List<FunctionWrapper> functions, Request request, AsyncHttpClient asyncHttpClient) {
        super(decoder, functions);
        this.request = request;
        this.asyncHttpClient = asyncHttpClient;
    }

    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.LONG_POLLING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
        hasSucceedOnce.set(true);
        return super.onStatusReceived(responseStatus);
    }

    @Override
    public String onCompleted() throws Exception {
        if (hasSucceedOnce.get()) {
            RequestBuilder r = new RequestBuilder();
            r.setUrl(request.uri())
                    .setMethod(request.method().name())
                    .setHeaders(request.headers());

            java.util.concurrent.Future<String> s = asyncHttpClient.prepareRequest(r.build()).execute(this);
        }

        return "";
    }
}

