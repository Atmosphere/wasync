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
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import org.atmosphere.client.Decoder;
import org.atmosphere.client.Function;
import org.atmosphere.client.Future;
import org.atmosphere.client.Request;
import org.atmosphere.client.Socket;
import org.atmosphere.client.Transport;

public class StreamTransport<T> extends AsyncCompletionHandler<String> implements Transport {

    private Future f;
    private Function<T> messageFunction = new NoOpsFunction();
    private Function<T> errorFunction = new NoOpsFunction();
    private Function<T> closeFunction = new NoOpsFunction();
    private Function<T> openFunction = new NoOpsFunction();
    private final Decoder<T> decoder;

    public StreamTransport(Decoder<T> decoder) {
        this.decoder = decoder;
    }

    @Override
    public Transport future(Future f) {
        this.f = f;
        return this;
    }

    @Override
    public Transport registerF(Function function) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public STATE onBodyPartReceived(final HttpResponseBodyPart content) throws Exception {
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    public STATE onStatusReceived(final HttpResponseStatus status) throws Exception {
        f.done();
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    public STATE onHeadersReceived(final HttpResponseHeaders headers) throws Exception {
        return STATE.CONTINUE;
    }

    @Override
    public String onCompleted(Response response) throws Exception {
        return "";
    }

    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.STREAMING;
    }

    /**
     * {@inheritDoc}
     */
    public void onThrowable(Throwable t) {
        f.cancel(true);
    }


    private final static class NoOpsFunction implements Function {

        @Override
        public void on(Object o) {

        }
    }
}

