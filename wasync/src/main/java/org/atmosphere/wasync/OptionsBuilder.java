/*
 * Copyright 2017 Async-IO.org
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

import java.util.concurrent.TimeUnit;

import org.asynchttpclient.AsyncHttpClient;

/**
 * Base class for building {@link Options}
 *
 * @author Jeanfrancois Arcand
 * @param <T>
 */
public abstract class OptionsBuilder<U extends Options, T extends OptionsBuilder<U,T>> {

    private Transport transport;
    private boolean reconnect = true;
    private int reconnectTimeoutInMilliseconds = 0;
    private int reconnectAttempts = 0;
    private long waitBeforeUnlocking = 2000;
    private AsyncHttpClient client;
    private boolean runtimeShared = false;
    private int requestTimeout = -1;
    protected final Class<T> derived;
    private boolean binary;

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
     * The time in seconds, before the reconnection occurs. Default is instant(0).
     *
     * @param reconnectInSecond time in seconds, before the reconnection occurs. Default is instant.
     * @return this
     */
    public T pauseBeforeReconnectInSeconds(int reconnectInSecond) {
        this.reconnectTimeoutInMilliseconds = (int)TimeUnit.SECONDS.toMillis(reconnectInSecond);
        return derived.cast(this);
    }
    
    /**
     * The time in milliseconds, before the reconnection occurs. Default is instant(0).
     *
     * @param reconnectTimeoutInMilliseconds time in milliseconds, before the reconnection occurs. Default is instant.
     * @return this
     */
    public T pauseBeforeReconnectInMilliseconds(int reconnectTimeoutInMilliseconds) {
        this.reconnectTimeoutInMilliseconds = reconnectTimeoutInMilliseconds;
        return derived.cast(this);
    }

    /**
     * Maximum reconnection attempts that will be executed if the connection is lost. Must be used in conjunction with {@link #reconnectInSeconds}
     */
    public T reconnectAttempts(int reconnectAttempts) {
        this.reconnectAttempts = reconnectAttempts;
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
     * by the library. Application must call {@link com.ning.http.client.AsyncHttpClient#close()} at the end of their
     * execution as the library won't close it since shared.
     *
     * @param client
     * @return this;
     */
    public T runtime(AsyncHttpClient client) {
        return runtime(client, true);
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
    public abstract U build();

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
     * The delay, in milliseconds, before reconnecting.
     * @return The delay, in milliseconds, before reconnecting.
     */
    public int reconnectTimeoutInMilliseconds(){
        return reconnectTimeoutInMilliseconds;
    }
    /**
     * Maximum reconnection attempts that will be executed if the connection is lost. Must be used in conjunction with {@link #reconnectInSeconds}
     *
     * @return the number of maximum reconnection attempts
     */
    public int reconnectAttempts(){
        return reconnectAttempts;
    }
    /**
     * The delay before considering the http connection has been fully processed by the server. By default, the library will wait 2 seconds before allowing the {@link Socket#fire(Object)}
     * to send message. Server side framework that aren't sending any data when suspending a connection may not be ready to fullfil request, hence some data may be lost.
     *
     * Framework like Atmosphere, Socket.io, Cometd do send some "handshake data" allow the fire operation to be available fast without loosing ant data.
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

    /**
     * Set to true in order to received byte data from the server. By default, binary aren't supported and response's server are considered as String.
     * @param binary true to enabled
     * @return this;
     */
    public T binary(boolean binary) {
        this.binary = binary;
        return derived.cast(this);
    }

    /**
     * Return true is server's response binary is supported. Default is false
     * @return true is server's response binary is supported. Default is false
     */
    public boolean binary() {
        return binary;
    }

}
