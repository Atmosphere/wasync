package org.atmosphere.tests;

import org.atmosphere.wasync.Request;

public class StreamingTest extends BaseTest {

    @Override
    Request.TRANSPORT transport() {
        return Request.TRANSPORT.STREAMING;
    }

    @Override
    int statusCode() {
        return 200;
    }
}
