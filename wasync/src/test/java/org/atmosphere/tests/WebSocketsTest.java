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
package org.atmosphere.tests;

import org.atmosphere.wasync.Request;
import org.testng.annotations.Test;

public class WebSocketsTest extends BaseTest {
    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.WEBSOCKET;
    }

    @Override
    int statusCode() {
        return 101;
    }

    // https://github.com/Atmosphere/nettosphere/issues/36
    @Override
    int notFoundCode() {
        return 101;
    }

    @Override
    int getCount() {
        return 1000;
    }

    //https://github.com/AsyncHttpClient/async-http-client/issues/277
    @Test(enabled = false)
    public void requestTimeoutTest() throws Exception {

    }
}
