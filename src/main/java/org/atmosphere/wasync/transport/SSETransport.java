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
package org.atmosphere.wasync.transport;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.FunctionResolver;

import java.util.List;

public class SSETransport<T> extends StreamTransport {

    public SSETransport(List<Decoder<?,?>>decoders, List<FunctionWrapper> functions, FunctionResolver resolver) {
        super(decoders, functions, resolver);
    }

    @Override
    public Request.TRANSPORT name() {
        return Request.TRANSPORT.SSE;
    }

}
