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

import java.io.IOException;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A Socket represent a connection to a remote server. A Socket abstract the transport used, the client will negotiate
 * the best {@link org.atmosphere.wasync.Request#transport()} to communicate with the remote Server. As simple as
 * <blockquote><pre>
     Client client = AtmosphereClientFactory.getDefault().newClient();

     RequestBuilder request = client.newRequestBuilder()
             .method(Request.METHOD.GET)
             .uri(targetUrl + "/suspend")
             .encoder(new Encoder&lt;String, Reader&gt;() {        // Stream the request body
                 &#64;Override
                 public Reader encode(String s) {
                     return new StringReader(s);
                 }
             })
             .decoder(new Decoder&lt;String, Reader&gt;() {
                  &#64;Override
                 public Reader decode(String s) {
                     return new StringReader(s);
                 }
             })
             .transport(Request.TRANSPORT.WEBSOCKET)                        // Try WebSocket
             .transport(Request.TRANSPORT.LONG_POLLING);                    // Fallback to Long-Polling

     Socket socket = client.create();
     socket.on("message", new Function&lt;String&gt;() {
         &#64;Override
         public void on(Reader r) {
             // Read the response
         }
     }).on(new Function&lt;IOException&gt;() {

         &#64;Override
         public void on(Throwable t) {
             // Some IOException occurred
         }

     }).open(request.build()).fire("echo");
 * </pre></blockquote>
 *
 * @author Jeanfrancois Arcand
 */
public interface Socket {

    /**
     * The current state of the underlying Socket.
     */
    public enum STATUS {
        /**
         * The socket is not yet connected
         */
        INIT,
        /**
         * The socket is open and ready to send messages
         */
        OPEN,
        /**
         * The socket is close
         */
        CLOSE,
        /**
         * The socket is broken
         */
        ERROR }

    /**
     * Send data to the remote Server.
     * @param data
     * @return a {@link Future}
     * @throws IOException
     */
    Future fire(Object data) throws IOException;

    /**
     * Associate a {@link Function} with the Socket. When a response is received, the library will try to associated
     * the decoded message (decoded by {@link Decoder}) to the defined type of the {@link Function}
     * @param function a {@link Function}
     * @return this
     */
    Socket on(Function<? extends Object> function);

    /**
     * Associate a {@link Function} with the Socket. When a response is received, the library will try to associated
     * the decoded message (decoded by {@link Decoder}) to the defined type of the {@link Function}. The default messages
     * are defined by {@link org.atmosphere.wasync.Function.EVENT_TYPE} but handling of custom message can be done using a {@link FunctionResolver}
     * @param function a {@link Function}
     * @return this
     */
    Socket on(String functionMessage, Function<? extends Object> function);

    /**
     * Connect to the remote Server using the {@link Request}'s information.
     * @param request a {@link Request}
     * @return this
     * @throws IOException
     */
    Socket open(Request request) throws IOException;
    
    /**
     * Connect to the remote Server using the {@link Request}'s information, will timeout if the connection failed to open 
     * within a certain time
     * @param request a {@link Request}
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return this
     * @throws IOException
     */
    Socket open(Request request, long timeout, TimeUnit unit) throws IOException;

    /**
     * Close this Socket
     */
    void close();

    /**
     *  Return true if this socket is open.
     */
    STATUS status();
}
