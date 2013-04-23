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
public interface Options {
    /**
     * The used {@link Transport}
     * @return {@link Transport}
     */
    public Transport transport() ;

    /**
     * Reconnect after a network failure or when the server close the connection.
     * @return reconnect
     */
    public boolean reconnect();

    /**
     * The delay, in second, before reconnecting.
     * @return The delay, in second, before reconnecting.
     */
    public int reconnectInSeconds();

    /**
     * When using long-polling and the {@link Request}, the delay before considering the long-polling connection has been fully processed by the server. If you use
     * the {@link org.atmosphere.wasync.impl.AtmosphereClient}, the server will send some handshake so this value is not needed.
     * @return the delay before considering the long-polling connection has been fully processed
     */
    public long waitBeforeUnlocking();

    /**
     * The {@link AsyncHttpClient} used to communicate with server.
     * @return {@link AsyncHttpClient} used to communicate with server.
     */
    public AsyncHttpClient runtime();

    /**
     * Set the {@link AsyncHttpClient}.
     * @param client the {@link AsyncHttpClient}
     */
    public void runtime(AsyncHttpClient client);

    /**
     * Return true is the {@link AsyncHttpClient} is shared between {@link Socket}. Default is false. You need to invoke {@link #runtime(com.ning.http.client.AsyncHttpClient)} to make
     * it shared.
     * @return true is the {@link AsyncHttpClient}
     */
    public boolean runtimeShared();

    /**
     * The time, in seconds to wait before closing the connection.
     * @return The time, in seconds to wait before closing the connection.
     */
    public int requestTimeoutInSeconds() ;
}
