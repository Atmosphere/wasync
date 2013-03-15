package org.atmosphere.wasync.impl;

import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Socket;

import com.ning.http.client.AsyncHttpClient;

public class AtmosphereClient extends DefaultClient {

	@Override
	protected Socket getSocket(AsyncHttpClient asyncHttpClient, Options options) {
		return new AtmosphereSocket(asyncHttpClient, options);
	}
}
