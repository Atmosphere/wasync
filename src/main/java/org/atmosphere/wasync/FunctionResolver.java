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
package org.atmosphere.wasync;

/**
 * FunctionResolver are useful for mapping received message with a {@link Function}. By default, only the predefined
 * function {@link Function.MESSAGE} are automatically mapped to Function,
 * <blockquote><pre>
     Client client = ClientFactory.getDefault().newClient();

     RequestBuilder request = client.newRequestBuilder()
             .method(Request.METHOD.GET)
             .uri(targetUrl + "/suspend")
             .decoder(new Decoder&lt;String, POJO&gt;() {
                 @Override
                 public POJO decode(String s) {
                     return new POJO(s);
                 }
             })
             .transport(Request.TRANSPORT.WEBSOCKET);

     Socket socket = client.create();
     socket.on(Function.MESSAGE.message.name(), new Function&lt;POJO&gt;() {
         @Override
         public void on(POJO t) {
             response.set(t);
         }
     })
 * </pre></blockquote>
 * or when a {@link Decoder} share the same type as the
 * defined Function. For example:
 * <blockquote><pre>
     Client client = ClientFactory.getDefault().newClient();

     RequestBuilder request = client.newRequestBuilder()
             .method(Request.METHOD.GET)
             .uri(targetUrl + "/suspend")
             .decoder(new Decoder&lt;String, POJO&gt;() {
                 @Override
                 public POJO decode(String s) {
                     return new POJO(s);
                 }
             })
             .transport(Request.TRANSPORT.WEBSOCKET);

     Socket socket = client.create();
     socket.on(new Function&lt;POJO&gt;() {
         @Override
         public void on(POJO t) {
             response.set(t);
         }
     })
 * </pre></blockquote>
 * An application can define its own Function.MESSAGE be writing the appropriate FunctionResolver.
 *
 * By default, the {@link org.atmosphere.wasync.impl.DefaultFunctionResolver} is used.
 *
 * @author Jeanfrancois Arcand
 */
public interface FunctionResolver {
    /**
     * Resolve the current message with
     * @param message the original response's body received
     * @param functionName the default function name taken from {@link Function.MESSAGE}
     * @param fn The current {@link FunctionWrapper}
     * @return true if the {@link Function} can be invoked.
     */
    boolean resolve(String message, Object functionName, FunctionWrapper fn);
}
