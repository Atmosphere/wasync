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
package org.atmosphere.wasync.transport;

import com.ning.http.client.RequestBuilder;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;

import java.util.List;

/**
 * Long-Polling {@link org.atmosphere.wasync.Transport} implementation
 *
 * @author Jeanfrancois Arcand
 */
public class LongPollingTransport extends StreamTransport {

    public LongPollingTransport(RequestBuilder requestBuilder, Options options, Request request, List<FunctionWrapper> functions) {
        super(requestBuilder, options, request, functions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.LONG_POLLING;
    }

}

