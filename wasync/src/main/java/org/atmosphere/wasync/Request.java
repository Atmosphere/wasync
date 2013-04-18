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
package org.atmosphere.wasync;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 * Define a request for {@link Socket}. This class cannot be instantiated. Instead, use the {@link RequestBuilder}
 *
 * @author Jeanfrancois Arcand
 */
public interface Request {

    public enum METHOD {GET, POST, TRACE, PUT, DELETE, OPTIONS}
    public enum TRANSPORT {WEBSOCKET, SSE, STREAMING, LONG_POLLING}

    /**
     * The list of transports to try
     * @return a {@link TRANSPORT}
     */
    List<TRANSPORT> transport();

    /**
     * The method
     * @return a {@link METHOD}
     */
    METHOD method();

    /**
     * Return the list of headers
     * @return a Map of headers
     */
    Map<String, Collection<String>> headers();

    /**
     * Return the list of query params
     * @return a Map of headers
     */
    Map<String, List<String>> queryString();

    /**
     * The list of {@link Encoder} to use before the request is sent.
     * @return The list of {@link Encoder}
     */
    List<Encoder<?,?>> encoders();

    /**
     * The list of {@link Decoder} to use before the request is sent.
     * @return The list of {@link Decoder}
     */
    List<Decoder<?,?>> decoders();

    /**
     * The targetted URI
     * @return the targetted URI
     */
    String uri();

    /**
     * The {@link FunctionResolver} associated with that request.
     * @return The {@link FunctionResolver}
     */
    FunctionResolver functionResolver();

}
