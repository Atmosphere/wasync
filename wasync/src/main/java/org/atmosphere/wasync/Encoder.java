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
 * Encode the request's body (or transform) of type U into an object of type T.
 *
 * @param <U>
 * @param <T>
 * @author Jeanfrancois Arcand
 */
public interface Encoder<U, T> {

    /**
     * Encode the object of type U into an object of type T.
     * @param s a request's body that has already been encoded or not
     * @return an encoded object.
     */
    T encode(U s);

}
