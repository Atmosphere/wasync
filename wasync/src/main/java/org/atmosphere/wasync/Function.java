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
 * A function is asynchronously invoked when a response is received, complete or not.
 * <p/>
 * This library supports predefined life cycle's events (@link Event} that can be used. For example, a Function
 * can be defined for handling IOException:
 * <blockquote><pre>
 * <p/>
 *     class Function&lt;IOException&gt;() {
 *         public void on(IOEXception ex) {
 *         }
 *     }
 * </pre></blockquote>
 * This function can be registered using the {@link Socket#on(Function)} as
 * <blockquote><pre>
 * <p/>
 *     socket.on(new Function&lt;IOEXception&gt;() {
 *         ....
 *     }
 * </pre></blockquote>
 * This is the equivalent of doing
 * <blockquote><pre>
 * <p/>
 *     socket.on(Event.ERROR, new Function&lt;IOEXception&gt;() {
 *         ....
 *     }
 * </pre></blockquote>
 * Anonymous functions call also be invoked if a {@link Decoder} match its type
 * <blockquote><pre>
 * <p/>
 *     socket.decoder(new Decoder&lt;String, POJO&gt;(){
 *         &#64;Override
 *         public POJO decode(Event e, String message) {
 *             return new POJO(message);
 *         }
 *     }
 * <p/>
 *     .on(new Function&lt;POJO&gt;() {
 *         ....
 *     }
 * </pre></blockquote>
 *
 * @param <T>
 * @author Jeanfrancois Arcand
 */
public interface Function<T> {

    /**
     * A function that will be invoked when a object of type T is defined.
     *
     * @param t  A function that will be invoked when a object of type T is defined.
     */
    void on(T t);

}
