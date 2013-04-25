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
 * Base class for building {@link Options}
 *
 * @author Jeanfrancois Arcand
 * @param <T>
 */
public abstract class OptionsBuilder<T extends OptionsBuilder<T>> {

    private Transport transport;
    private boolean reconnect = true;
    private int reconnectInSecond = 0;
    private long waitBeforeUnlocking = 2000;
    private AsyncHttpClient client;
    private boolean runtimeShared = false;
    private int requestTimeout = -1;
    protected final Class<T> derived;

    protected OptionsBuilder(Class<T> derived) {
        this.derived = derived;
    }

    /**
     * The time, in seconds, the connection will stay open when waiting for new messages. This can be seen as the idle time.
     *
     * @return this
     */
    public T requestTimeoutInSeconds(int requestTimeout) {
        this.requestTimeout = requestTimeout;
        return derived.cast(this);
    }

    /**
     * Register a new {@link Transport} implementation. Register a transport only if you are planning to use
     * a different transport than the supported one.
     *
     * @param transport {@link Transport}
     * @return this
     */
    public T registerTransport(Transport transport) {
        this.transport = transport;
        return derived.cast(this);
    }

    /**
     * Automatically reconnect in case the connection get closed. Default is <tt>true</tt>
     *
     * @param reconnect reconnect in case the connection get closed. Default is <tt>true</tt>
     * @return this
     */
    public T reconnect(boolean reconnect) {
        this.reconnect = reconnect;
        return derived.cast(this);
    }

    /**
     * The time in second, before the reconnection occurs. Default is 1 second
     *
     * @param reconnectInSecond time in second, before the reconnection occurs. Default is 1 second
     * @return this
     */
    public T pauseBeforeReconnectInSeconds(int reconnectInSecond) {
        this.reconnectInSecond = reconnectInSecond;
        return derived.cast(this);
    }

    /**
     * For streaming and long-polling, the server may not send the headers so the client never knows
     * if the connection succeeded or not. By default the library will wait for 2500 milliseconds before
     * considering the connection established.
     *
     * @param waitBeforeUnlocking the time in millisecond
     * @return this
     */
    public T waitBeforeUnlocking(long waitBeforeUnlocking) {
        this.waitBeforeUnlocking = waitBeforeUnlocking;
        return derived.cast(this);
    }

    /**
     * Set to true if your AsyncHttpClient is shared between clients.
     * by the library.
     *
     * @param runtimeShared true if your AsyncHttpClient is shared between clients.
     * @return this;
     */
    public T runtimeShared(boolean runtimeShared) {
        this.runtimeShared = runtimeShared;
        return derived.cast(this);
    }

    /**
     * Allow an application that want to share {@link AsyncHttpClient} or configure it before it gets used
     * by the library.
     *
     * @param client
     * @return this;
     */
    public T runtime(AsyncHttpClient client) {
        return runtime(client, false);
    }

    /**
     * Allow an application that want to share {@link AsyncHttpClient} or configure it before it gets used
     * by the library.
     *
     * @param client
     * @param runtimeShared to true if the runtime is shared between clients. If shared, the asyncHttpClient.close()
     *                      must be invoked by the application itself.
     * @return this;
     */
    public T runtime(AsyncHttpClient client, boolean runtimeShared) {
        this.client = client;
        this.runtimeShared = runtimeShared;
        return derived.cast(this);
    }

    /**
     * Build an {@link Options}
     *
     * @return {@link Options}
     */
    public abstract Options build();

    /**
     * The used {@link Transport}
     * @return {@link Transport}
     */
    public Transport transport() {
        return transport;
    }
    /**
     * Reconnect after a network failure or when the server close the connection.
     * @return reconnect
     */
    public boolean reconnect(){
        return reconnect;
    }
    /**
     * The delay, in second, before reconnecting.
     * @return The delay, in second, before reconnecting.
     */
    public int reconnectInSeconds(){
        return reconnectInSecond;
    }
    /**
     * When using long-polling and the {@link Request}, the delay before considering the long-polling connection has been fully processed by the server. If you use
     * the {@link org.atmosphere.wasync.impl.AtmosphereClient}, the server will send some handshake so this value is not needed.
     * @return the delay before considering the long-polling connection has been fully processed
     */
    public long waitBeforeUnlocking() {
        return waitBeforeUnlocking;
    }
    /**
     * The {@link AsyncHttpClient} used to communicate with server.
     * @return {@link AsyncHttpClient} used to communicate with server.
     */
    public AsyncHttpClient runtime(){
        return client;
    }
    /**
     * Return true is the {@link AsyncHttpClient} is shared between {@link Socket}. Default is false. You need to invoke {@link #runtime(com.ning.http.client.AsyncHttpClient)} to make
     * it shared.
     * @return true is the {@link AsyncHttpClient}
     */
    public boolean runtimeShared(){
        return runtimeShared;
    }
    /**
     * The time, in seconds to wait before closing the connection.
     * @return The time, in seconds to wait before closing the connection.
     */
    public int requestTimeoutInSeconds() {
        return requestTimeout;
    }

}
