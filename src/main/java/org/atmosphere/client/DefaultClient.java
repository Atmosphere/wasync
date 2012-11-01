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
package org.atmosphere.client;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketTextListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DefaultClient implements Client {

    private AsyncHttpClient asyncHttpClient;

    protected DefaultClient() {
    }

    public DefaultClient open(Options options) {
        AsyncHttpClientConfig.Builder config = new AsyncHttpClientConfig.Builder();
        this.asyncHttpClient = new AsyncHttpClient(config.build());
        return this;
    }

    public Future fire(Request request) throws IOException {
        RequestBuilder r = new RequestBuilder();

        List<Transport> transports = getTransport(request.transport());

        Transport primary = transports.get(0);
        // TODO: Fallback implementation

        Future f;
        if (primary.name().equals(Request.TRANSPORT.WEBSOCKET)) {
            java.util.concurrent.Future<WebSocket> w = asyncHttpClient.prepareRequest(r.build()).execute(
                    new WebSocketUpgradeHandler.Builder().addWebSocketListener((WebSocketTextListener) primary).build());
            try {
                f = new Future(new SocketImpl(request, w.get(), primary));
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else {
            java.util.concurrent.Future<String> s = asyncHttpClient.prepareRequest(r.build()).execute(
                    (AsyncCompletionHandler<String>) primary);

            // TODO: This is no garantee the connection has been established.
            f = new Future(new SocketImpl(request, asyncHttpClient, primary));
        }

        primary.future(f);
        return f;
    }

    public DefaultClient close() {
        if (asyncHttpClient != null) {
            asyncHttpClient.closeAsynchronously();
        }
        return this;
    }

    protected List<Transport> getTransport(List<Request.TRANSPORT> t) {

        List<Transport> transports = new ArrayList<Transport>();

        if (t.equals(Request.TRANSPORT.WEBSOCKET)) {
            transports.add(new WebSocketTransport());
        } else if (t.equals(Request.TRANSPORT.SSE)) {
            transports.add(new SSETransport());
        } else if (t.equals(Request.TRANSPORT.LONG_POLLING)) {
            transports.add(new LongPollingTransport());
        } else if (t.equals(Request.TRANSPORT.STREAMING)) {
            transports.add(new StreamTransport());
        }
        return transports;
    }

    private final static class WebSocketTransport<T> implements WebSocketTextListener, Transport {

        Future f;
        Function<T> messageFunction = new NoOpsFunction();
        Function<T> errorFunction = new NoOpsFunction();
        Function<T> closeFunction = new NoOpsFunction();
        Function<T> openFunction = new NoOpsFunction();

        @Override
        public Transport future(Future f) {
            this.f = f;
            return this;
        }

        @Override
        public Transport injectFunction(Socket.EVENT event, Function function) {
            return null;
        }

        @Override
        public void onMessage(String message) {
            messageFunction.on((T) message);
        }

        @Override
        public void onFragment(String fragment, boolean last) {

        }

        @Override
        public void onOpen(WebSocket websocket) {
            f.done();
            openFunction.on((T)"");
        }

        @Override
        public void onClose(WebSocket websocket) {
            closeFunction.on((T)"");
        }

        @Override
        public void onError(Throwable t) {
            f.cancel(true);
            closeFunction.on((T)t);
        }

        @Override
        public Request.TRANSPORT name() {
            return Request.TRANSPORT.WEBSOCKET;
        }
    }

    private final static class StreamTransport<T> extends AsyncCompletionHandler<String> implements Transport {

        Future f;
        Function<T> messageFunction = new NoOpsFunction();
        Function<T> errorFunction = new NoOpsFunction();
        Function<T> closeFunction = new NoOpsFunction();
        Function<T> openFunction = new NoOpsFunction();

        @Override
        public Transport future(Future f) {
            this.f = f;
            return this;
        }

        @Override
        public Transport injectFunction(Socket.EVENT event, Function function) {
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

    }

    private final static class SSETransport<T> extends AsyncCompletionHandler<String> implements Transport {

        Future f;
        Function<T> messageFunction = new NoOpsFunction();
        Function<T> errorFunction = new NoOpsFunction();
        Function<T> closeFunction = new NoOpsFunction();
        Function<T> openFunction = new NoOpsFunction();

        @Override
        public Transport future(Future f) {
            this.f = f;
            return this;
        }

        @Override
        public Transport injectFunction(Socket.EVENT event, Function function) {
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
            return Request.TRANSPORT.SSE;
        }

        /**
         * {@inheritDoc}
         */
        public void onThrowable(Throwable t) {
            f.cancel(true);
        }
    }

    private final static class LongPollingTransport<T> extends AsyncCompletionHandler<String> implements Transport {

        Future f;
        Function<T> messageFunction = new NoOpsFunction();
        Function<T> errorFunction = new NoOpsFunction();
        Function<T> closeFunction = new NoOpsFunction();
        Function<T> openFunction = new NoOpsFunction();

        @Override
        public Transport future(Future f) {
            this.f = f;
            return this;
        }

        @Override
        public Transport injectFunction(Socket.EVENT event, Function function) {
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
            return Request.TRANSPORT.LONG_POLLING;
        }

        /**
         * {@inheritDoc}
         */
        public void onThrowable(Throwable t) {
            f.cancel(true);
        }
    }

    private final static class NoOpsFunction implements Function{

        @Override
        public void on(Object o) {

        }
    }
}
