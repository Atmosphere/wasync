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

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.FluentStringsMap;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Transport;

import java.util.Arrays;
import java.util.List;

/**
 * Atmosphere Specific protocol information.
 *
 * @author Jeanfrancois Arcand
 */
public class AtmosphereSocketRuntime extends SocketRuntime {

    public AtmosphereSocketRuntime(Transport transport, Options options, DefaultFuture rootFuture, List<FunctionWrapper> functions) {
        super(transport, options, rootFuture, functions);
    }

    @Override
    protected AsyncHttpClient.BoundRequestBuilder configureAHC(Request request) {
        FluentStringsMap m = DefaultSocket.decodeQueryString(request);
        m.put("X-Atmosphere-Transport", Arrays.asList(new String[]{"polling"}));
        m.remove("X-atmo-protocol");

        return options.runtime().preparePost(request.uri())
                .setHeaders(request.headers())
                .setQueryParams(m)
                .setMethod(Request.METHOD.POST.name());
    }
}
