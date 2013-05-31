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

import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.FunctionResolver;
import org.atmosphere.wasync.Socket;

import static org.atmosphere.wasync.impl.AtmosphereRequest.AtmosphereRequestBuilder;

/**
 * A specialized {@link Client} for the Atmosphere Framework. This client support the Atmosphere Protocol.
 * <br/>
 * An {@link AtmosphereRequestBuilder} will be created and Atmosphere's specific protocol information can be set using this object.
 *
 * @author Jeanfrancois Arcand
 */
public class AtmosphereClient implements Client<DefaultOptions, DefaultOptionsBuilder,AtmosphereRequest.AtmosphereRequestBuilder> {

    public AtmosphereClient() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket create() {
        return ClientUtil.create();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket create(DefaultOptions options) {
        return ClientUtil.create(options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AtmosphereRequestBuilder newRequestBuilder(Class<AtmosphereRequest.AtmosphereRequestBuilder> clazz) {
        AtmosphereRequestBuilder b;
        try {
            b = clazz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return AtmosphereRequestBuilder.class.cast(b.resolver(FunctionResolver.DEFAULT));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultOptionsBuilder newOptionsBuilder() {
        return new DefaultOptionsBuilder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AtmosphereRequestBuilder newRequestBuilder() {
        AtmosphereRequestBuilder b = new AtmosphereRequestBuilder();
        return AtmosphereRequestBuilder.class.cast(b.resolver(FunctionResolver.DEFAULT));
    }

}
