package org.atmosphere.tests;

import org.atmosphere.client.Request;

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
