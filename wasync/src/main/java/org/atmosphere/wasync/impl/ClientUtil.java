/*
 * Copyright 2013 Jeanfrancois Arcand
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

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Socket;

/**
 * Util class for building {@link AsyncHttpClient}
 *
 * @author Jeanfrancois Arcand
 */
public class ClientUtil {
    private static final String WASYNC_USER_AGENT = "wAsync/1.0";

    public final static AsyncHttpClient createDefaultAsyncHttpClient(Options o) {
		AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        int t = o.requestTimeoutInSeconds();
		b.setFollowRedirects(true).setRequestTimeoutInMs(t == -1? t : t * 1000).setUserAgent(WASYNC_USER_AGENT);
        AsyncHttpClientConfig config = b.build();
		return new AsyncHttpClient(config);
	}

    public static Socket create(Options options) {
        AsyncHttpClient asyncHttpClient = options.runtime();
        if (asyncHttpClient == null || asyncHttpClient.isClosed()) {
            asyncHttpClient = ClientUtil.createDefaultAsyncHttpClient(options);
            options.runtime(asyncHttpClient);
        }
        return getSocket(options);
    }

    public final static Socket getSocket(Options options) {
        return new DefaultSocket(options);
    }

    public  static Socket create() {
        AsyncHttpClient asyncHttpClient = createDefaultAsyncHttpClient(new DefaultOptionsBuilder().reconnect(true).build());

        return getSocket(new DefaultOptionsBuilder().runtime(asyncHttpClient, false).build());
    }
}
