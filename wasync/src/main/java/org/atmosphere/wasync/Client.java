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

/**
 * An asynchronous client's implementation used to create {@link Socket} and {@link Request}. As simple as as
 * <blockquote><pre>
     Client client = ClientFactory.getDefault().newClient();

     RequestBuilder request = client.newRequestBuilder()
             .method(Request.METHOD.GET)
             .uri(targetUrl + "/suspend")
             .decoder(new Decoder&lt;String, POJO&gt;() {
                 &#64;Override
                 public POJO decode(String s) {
                     return new POJO(s);
                 }
             })
             .transport(Request.TRANSPORT.WEBSOCKET);
 * </pre></blockquote>
 * @author Jeanfrancois Arcand
 */
public interface Client<O extends Options, U extends OptionsBuilder, T extends RequestBuilder> {

    /**
     * Create a {@link Socket}
     *
     * @return {@link Socket}
     */
    Socket create();

    /**
     * Create a {@link Socket} configured using the {@link Options}
     *
     * @return {@link Socket}
     */
    Socket create(O options);

    /**
     * Return a {@link RequestBuilder}
     *
     * @return a {@link RequestBuilder}
     */
    T newRequestBuilder();

    /**
     * Create a new {@link RequestBuilder} based on the class' implementation.
     *
     * @param clazz an implementation of {@link RequestBuilder}
     * @return a {@link RequestBuilder}
     */
    T newRequestBuilder(Class<T> clazz);


    /**
     * Return an {@link OptionsBuilder}
     *
     * @return {@link OptionsBuilder}
     */
    U newOptionsBuilder();
}
