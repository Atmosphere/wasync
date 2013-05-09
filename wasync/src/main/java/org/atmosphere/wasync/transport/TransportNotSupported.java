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
package org.atmosphere.wasync.transport;

/**
 * An exception thrown when a {@link org.atmosphere.wasync.Transport} is not supported by the server. The exception can be handled using a Function:
 * <blockquote><pre>
 * <p/>
 *     class Function&lt;TransportNotSupported&gt;() {
 *         public void on(TransportNotSupported ex) {
 *         }
 *     }
 * </pre></blockquote>
 * @author Jeanfrancois Arcand
 */
public class TransportNotSupported extends Exception {

    private final int statusCode;
    private final String reasonPhrase;

    public TransportNotSupported(int statusCode, String reasonPhrase){
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    @Override
    public String toString(){
        return "Connection Error " + statusCode + " : " + reasonPhrase;
    }
}
