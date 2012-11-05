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
package org.atmosphere.client.impl;

import org.atmosphere.client.Decoder;
import org.atmosphere.client.Encoder;
import org.atmosphere.client.FunctionResolver;
import org.atmosphere.client.Request;
import org.atmosphere.client.RequestBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultRequest implements Request {

    protected final DefaultRequestBuilder builder;

    protected DefaultRequest(DefaultRequestBuilder builder) {
        this.builder = builder;
    }

    @Override
    public List<TRANSPORT> transport() {
        return builder.transports;
    }

    @Override
    public METHOD method() {
        return builder.method;
    }

    @Override
    public Map<String, Collection<String>> headers() {
        return builder.headers;
    }

    @Override
    public Map<String, Collection<String>> queryString() {
        return builder.queryString;
    }

    @Override
    public List<Encoder<?,?>> encoders() {
        return builder.encoders;
    }

    @Override
    public List<Decoder<?,?>> decoders() {
        return builder.decoders;
    }

    @Override
    public String uri() {
        return builder.uri;
    }

    @Override
    public FunctionResolver functionResolver() {
        return builder.resolver;
    }
}
