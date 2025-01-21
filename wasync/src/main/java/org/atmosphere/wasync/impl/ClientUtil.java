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
package org.atmosphere.wasync.impl;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Socket;

/**
 * Util class for building {@link AsyncHttpClient}
 *
 * @author Jeanfrancois Arcand
 */
public class ClientUtil {
    private static final String WASYNC_USER_AGENT = "wAsync/2.0";

    public final static AsyncHttpClient createDefaultAsyncHttpClient(Options o) {
		return createDefaultAsyncHttpClient(o.requestTimeoutInSeconds());
	}

	public final static AsyncHttpClient createDefaultAsyncHttpClient(int requestTimeoutInSeconds) {
		DefaultAsyncHttpClientConfig.Builder b = new DefaultAsyncHttpClientConfig.Builder();
		b.setFollowRedirect(true).setTcpNoDelay(true).setKeepAlive(true).setConnectTimeout(-1)
				.setReadTimeout(requestTimeoutInSeconds == -1 ? requestTimeoutInSeconds : requestTimeoutInSeconds * 1000).setUserAgent(WASYNC_USER_AGENT);
		return new DefaultAsyncHttpClient(b.build());
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
