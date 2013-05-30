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
 *  This class define the type of event a {@link Function} or {@link Decoder} will be called with.
 *
 *  @author Jeanfrancois Arcand
 */
public enum Event {

    /**
     * This event is fired when the connection is open. This event is only fired once.
     */
    OPEN,

    /**
     * This event is fired when the connection gets closed. This event is only fired once.
     */
    CLOSE,

    /**
     * This event is fired every time a new message is received.
     */
    MESSAGE,

    /**
     * This event is fired every time the connection re-opened
     */
    REOPENED,

    /**
     * This event is fired when unexpected error happens. This event is only fired once.
     */
    ERROR,

    /**
     * This event is fire when the response's status is received. This event is only fired once.
     */
    STATUS,

    /**
     * This event is fire when the response's headers is received. This event is only fired once.
     */
    HEADERS,

    /**
     * This event is fire when the response's bytes are received.
     */
    MESSAGE_BYTES,

    /**
     * This event is fire when the connection has been established. The Transport propagated is the one that worked.
     * This event is only fired once.
     */
    TRANSPORT

}
