/*
 * Copyright 2008-2025 Async-IO.org
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
 * A decoder can be used to 'decode' the response's body. Decoder's can be used to decode the response body and match them with {@link Function}'s implementation.
 * This library will try to match the decoded Object to its associated Function. For example, the Decoder's type will
 * be mapped, by the {@link FunctionResolver} to the Function of the same type if no function message has been defined:
 *
 * <blockquote><pre>

   Decoder&lt;String, POJO&gt; d = new Decoder&lt;String, POJO&gt;() {
             &#64;Override
             public POJO decode(Event e, String s) {
                 return new POJO(s);
             }
         }
 * </pre></blockquote>
 * Important: Decoder cannot be chained, e.g the result of a Decoder will never be passed to another Decoder, like {@link Encoder}
 * @param <U> origin type
 * @param <T> destination type
 * @author Jeanfrancois Arcand
 */
public interface Decoder<U, T> {
    /**
     * Decode the specified object of type U into object of type T.
     *
     * @param e an {@link Event} type. This can be used to differentiate event received.
     * @param s a object of type U
     * @return a new object of type T
     */
    T decode(Event e, U s);

    /**
     * A Decoder may return an instance of a Decoded object to prevent some messages from being delivered to a {@link Function},
     * by returning a {@link Decoded#ABORT} instance. Returning an instance of Decoded with the action set to {@link Decoded.ACTION#CONTINUE}
     * will allow dispatching messages to {@link Function}
     * @param <T> destination type
     */
    public final static class Decoded<T> {
        /**
         * Use this object to prevent the delivering of messages to {@link Function}
         */
        public final static Decoded ABORT = new Decoded(null, ACTION.ABORT);

        public enum ACTION {
            /**
             * Continue the decoding/dispatching process.
             */
            CONTINUE,
            /**
             * Do not dispatch the message to {@link Function}
             */
            ABORT}

        private final T decodedMessage;
        private final ACTION action;

        /**
         * Create a decoded object
         * @param decodedMessage the decoded message
         * @param action the action
         */
        public Decoded(T decodedMessage, ACTION action) {
            this.decodedMessage = decodedMessage;
            this.action = action;
        }

        /**
         * Create a decoded object with action set to {@link ACTION#CONTINUE}
         * @param decodedMessage the decoded Message
         */
        public Decoded(T decodedMessage) {
            this.decodedMessage = decodedMessage;
            this.action = ACTION.CONTINUE;
        }

        /**
         * Return the decoded message.
         * @return the decoded message.
         */
        public T decoded() {
            return decodedMessage;
        }

        /**
         * Return the action.
         * @return the action.
         */
        public ACTION action() {
            return action;
        }
    }

}
