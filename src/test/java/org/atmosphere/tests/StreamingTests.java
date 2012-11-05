package org.atmosphere.tests;

import org.atmosphere.client.Request;

public class StreamingTests extends BaseTests {

    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.STREAMING;
    }

    @Override
    int statusCode() {
        return 200;
    }
}
