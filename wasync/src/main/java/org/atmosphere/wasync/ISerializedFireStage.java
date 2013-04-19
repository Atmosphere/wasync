package org.atmosphere.wasync;

import org.atmosphere.wasync.impl.SequentialHTTPSocket;

import com.google.common.util.concurrent.SettableFuture;
import com.ning.http.client.Response;

public interface ISerializedFireStage {

	public void setSocket(SequentialHTTPSocket socket);
	
	public void enqueue(Object firePayload, SettableFuture<Response> originalFuture);	
	
}
