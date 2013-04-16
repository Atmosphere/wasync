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
package org.atmosphere.wasync;

import com.ning.http.client.AsyncHttpClient;

/**
 * Configure the underlying WebSocket/HTTP client.
 *
 * @author Jeanfrancois Arcand
 */
public class Options {

    private final OptionsBuilder b;

    private Options(OptionsBuilder b) {
        this.b = b;
    }

    public Transport transport() {
        return b.transport;
    }

    public boolean reconnect(){
        return b.reconnect;
    }

    public int reconnectInSeconds(){
        return b.reconnectInSecond;
    }

    public long waitBeforeUnlocking() {
        return b.waitBeforeUnlocking;
    }

    public AsyncHttpClient runtime(){
        return b.client;
    }

    public void runtime(AsyncHttpClient client){
        b.client = client;
    }

    public boolean isShared(){
        return b.runtimeShared;
    }

    public int requestTimeout() {
        return b.requestTimeout;
    }

    public final static class OptionsBuilder {

        private Transport transport;
        private boolean reconnect = true;
        private int reconnectInSecond = 0;
        private long waitBeforeUnlocking = 2500;
        private AsyncHttpClient client;
        private boolean runtimeShared = false;
        private int requestTimeout = -1;

        /**
         * The time, in seconds, the connection will stay open when waiting for new messages. This can be seen as the idle time.
         * @return this
         */
        public OptionsBuilder requestTimeout(int requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Register a new {@link Transport} implementation. Register a transport only if you are planning to use
         * a different transport than the supported one.
         * @param transport {@link Transport}
         * @return this
         */
        public OptionsBuilder registerTransport(Transport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Automatically reconnect in case the connection get closed. Default is <tt>true</tt>
         * @param reconnect reconnect in case the connection get closed. Default is <tt>true</tt>
         * @return this
         */
        public OptionsBuilder reconnect(boolean reconnect) {
            this.reconnect = reconnect;
            return this;
        }

        /**
         * The time in second, before the reconnection occurs. Default is 1 second
         * @param reconnectInSecond time in second, before the reconnection occurs. Default is 1 second
         * @return this
         */
        public OptionsBuilder pauseBeforeReconnectInSeconds(int reconnectInSecond) {
            this.reconnectInSecond = reconnectInSecond;
            return this;
        }

        /**
         * For streaming and long-polling, the server may not send the headers so the client never knows
         * if the connection succeeded or not. By default the library will wait for 2500 milliseconds before
         * considering the connection established.
         *
         * @param waitBeforeUnlocking the time in millisecond
         * @return this
         */
        public OptionsBuilder waitBeforeUnlocking(long waitBeforeUnlocking) {
            this.waitBeforeUnlocking = waitBeforeUnlocking;
            return this;
        }

        /**
         * Set to true if your AsyncHttpClient is shared between clients.
         * by the library.
         * @param runtimeShared true if your AsyncHttpClient is shared between clients.
         * @return this;
         */
        public OptionsBuilder runtimeShared(boolean runtimeShared) {
            this.runtimeShared = runtimeShared;
            return this;
        }

        /**
         * Allow an application that want to share {@link AsyncHttpClient} or configure it before it gets used
         * by the library.
         * @param client
         * @return this;
         */
        public OptionsBuilder runtime(AsyncHttpClient client) {
            return runtime(client, false);
        }

        /**
         * Allow an application that want to share {@link AsyncHttpClient} or configure it before it gets used
         * by the library.
         * @param client
         * @param runtimeShared to true if the runtime is shared between clients. If shared, the asyncHttpClient.close()
         *                      must be invoked by the application itself.
         * @return this;
         */
        public OptionsBuilder runtime(AsyncHttpClient client, boolean runtimeShared) {
            this.client = client;
            this.runtimeShared = runtimeShared;
            return this;
        }

        public Options build(){
            return new Options(this);
        }
    }
}
