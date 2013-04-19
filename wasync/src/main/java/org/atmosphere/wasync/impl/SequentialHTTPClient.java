package org.atmosphere.wasync.impl;

import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.impl.DefaultClient;


public class SequentialHTTPClient extends DefaultClient {
	
    protected Socket getSocket(Options options) {
    	return new SequentialHTTPSocket(options);
    }

}
