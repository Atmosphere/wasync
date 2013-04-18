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

import java.util.List;

/**
 * A {@link Decoder} that always return a List of message to be dispatched one by one to the remaining list of Decoder.
 *
 * @author Jeanfrancois Arcand
 */
public interface ReplayDecoder extends Decoder<String, List<?>> {

    /**
     * Decode a String into a List of Objects. Each element of the List will be dispatched to the decoders that where
     * added after an implementation of that interface.
     *
     *
     * @param e Event
     * @param s a object of type U
     * @return a List of Object
     */
    @Override
    public List<?> decode(Event e, String s);
}
