/*
 * Copyright 2008-2022 Async-IO.org
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
package org.atmosphere.wasync.decoder;

import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Decoder.Decoded;
import org.atmosphere.wasync.Event;

/**
 * Trim white space protocol sent by the Atmosphere's org.atmosphere.interceptor.PaddingAtmosphereInterceptor,
 * discard heartbeat message from the server.
 *
 * @author Jean-Francois Arcand
 */
public class PaddingAndHeartbeatDecoder implements Decoder<String, Decoded<String>> {

    private final int paddingSize;
    private final String heartbeatChar;

    public PaddingAndHeartbeatDecoder(){
        this(4098, "X");
    }

    public PaddingAndHeartbeatDecoder(int paddingSize, String heartbeatChar) {
        this.paddingSize = paddingSize;
        this.heartbeatChar = heartbeatChar;
    }

    @Override
    public Decoded<String>  decode(Event type, String message) {
        if (type.equals(Event.MESSAGE)) {

            if (message.equalsIgnoreCase(heartbeatChar)) {
                return new Decoded<String>(message, Decoded.ACTION.ABORT);
            }

            message = ltrim(message);
            if (message == null) {
                return new Decoded<String>(message, Decoded.ACTION.ABORT);
            }
        }
        return new Decoded<String>(message);
    }

    private String ltrim(String s) {

        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }

        return i == paddingSize ? s.trim().length() == 0 ? null : s.substring(i) : s;
    }

}
