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

import org.atmosphere.wasync.RequestBuilder;

/**
 * Default implementation of the {@link org.atmosphere.wasync.RequestBuilder}
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultRequestBuilder extends RequestBuilder<DefaultRequestBuilder> {

    public DefaultRequestBuilder() {
        super(DefaultRequestBuilder.class);
    }

    protected DefaultRequestBuilder(Class<DefaultRequestBuilder> derived) {
        super(DefaultRequestBuilder.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DefaultRequest build() {
        return new DefaultRequest(this);
    }
}
