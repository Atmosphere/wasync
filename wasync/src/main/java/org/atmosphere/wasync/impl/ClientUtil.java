/*
 * Copyright 2014 Jeanfrancois Arcand
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
package org.atmosphere.wasync.impl;

import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Socket;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpProviderConfig;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;

/**
 * Util class for building {@link AsyncHttpClient}
 *
 * @author Jeanfrancois Arcand
 */
public class ClientUtil {
    private static final String WASYNC_USER_AGENT = "wAsync/2.0";

    public final static AsyncHttpClient createDefaultAsyncHttpClient(Options o) {
    	NettyAsyncHttpProviderConfig nettyConfig = new NettyAsyncHttpProviderConfig();
        nettyConfig.addProperty("child.tcpNoDelay", "true");
        nettyConfig.addProperty("child.keepAlive", "true");
        return createDefaultAsyncHttpClient(o.requestTimeoutInSeconds(), nettyConfig);  
    }
    
    public final static AsyncHttpClient createDefaultAsyncHttpClient(Options o, AsyncHttpProviderConfig asyncHttpProviderConfig) {
        return createDefaultAsyncHttpClient(o.requestTimeoutInSeconds(), asyncHttpProviderConfig);  
    }
    
    public final static AsyncHttpClient createDefaultAsyncHttpClient(int requestTimeoutInSeconds, AsyncHttpProviderConfig asyncHttpProviderConfig) {
        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        b.setFollowRedirect(true).setConnectionTimeout(-1).setReadTimeout(requestTimeoutInSeconds == -1 ? requestTimeoutInSeconds : requestTimeoutInSeconds * 1000).setUserAgent(WASYNC_USER_AGENT);
        AsyncHttpClientConfig config = b.setAsyncHttpClientProviderConfig(asyncHttpProviderConfig).build();
        return new AsyncHttpClient(config);
    }
       
    public static Socket create(Options options) {
        return create(options, DefaultSocket.class);
    }

    public static Socket create(Options options, Class<? extends Socket> socket) {
        AsyncHttpClient asyncHttpClient = options.runtime();
        if (asyncHttpClient == null || asyncHttpClient.isClosed()) {
            asyncHttpClient = ClientUtil.createDefaultAsyncHttpClient(options);
            options.runtime(asyncHttpClient);
        }
        return getSocket(options, socket);
    }

    public final static Socket getSocket(Options options, Class<? extends Socket> socket) {
        try {
            return socket.getConstructor(Options.class).newInstance(options);
        } catch (Exception e) {
            return new DefaultSocket(options);
        }
    }

    public static Socket create(Class<? extends Socket> socket) {
        AsyncHttpClient asyncHttpClient = createDefaultAsyncHttpClient(new DefaultOptionsBuilder().reconnect(true).build());

        return getSocket(new DefaultOptionsBuilder().runtime(asyncHttpClient, false).build(), socket);
    }

    public static Socket create() {
        AsyncHttpClient asyncHttpClient = createDefaultAsyncHttpClient(new DefaultOptionsBuilder().reconnect(true).build());

        return getSocket(new DefaultOptionsBuilder().runtime(asyncHttpClient, false).build(), DefaultSocket.class);
    }
}
