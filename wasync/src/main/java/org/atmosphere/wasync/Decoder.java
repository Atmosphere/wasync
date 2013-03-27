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
 * A decoder can be used to 'decode' the response's body. Decoder can be chained amongst themselves in order to create
 * plain Java Object. Decoder's can be used to decode the response body and match them with {@link Function}'s implementation.
 * This library will try to match the decoded Object to its associated Function. For example, the Decoder's type will
 * be mapped, by the {@link FunctionResolver} to the Function of the same type if no function message has been defined:
 *
 * <blockquote><pre>

   Decoder&lt;String, POJO&gt; d = new Decoder&lt;String, POJO&gt;() {
             &#64;Override
             public POJO decode(Transport.EVENT_TYPE e, String s) {
                 return new POJO(s);
             }
         }

   Function&lt;POJO&gt; f = new Function&lt;POJO&gt;() {
             &#64;Override
             public void on(Transport.EVENT_TYPE e, POJO t) {

             }
        }

 * </pre></blockquote>
 * @param <U>
 * @param <T>
 * @author Jeanfrancois Arcand
 */
public interface Decoder<U extends Object, T> {
    /**
     * Decode the specified object of type U into object of type T
     * @param e Transport.EVENT_TYPE
     * @param s a object of type U
     * @return a new object of type T
     */
    T decode(Transport.EVENT_TYPE e, U s);

}
