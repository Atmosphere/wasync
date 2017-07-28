package org.atmosphere.wasync.transport;


import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseHeaders;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;

public class WebsocketUpgradeHandlerWrapper implements AsyncHandler<WebSocketUpgradeHandler>{
	
	private final AsyncHandler<WebSocketUpgradeHandler> asyncHandler;
	
	public WebsocketUpgradeHandlerWrapper(AsyncHandler<WebSocketUpgradeHandler> asyncHandler) {
		this.asyncHandler = asyncHandler;
	}

	@Override
	public void onThrowable(Throwable t) {
		asyncHandler.onThrowable(t);
	}

	@Override
	public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
		return asyncHandler.onBodyPartReceived(bodyPart);
	}

	@Override
	public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
		return asyncHandler.onStatusReceived(responseStatus);
	}

	@Override
	public State onHeadersReceived(HttpResponseHeaders headers) throws Exception {
		return asyncHandler.onHeadersReceived(headers);
	}

	@Override
	public WebSocketUpgradeHandler onCompleted() throws Exception {
		return asyncHandler.onCompleted();
	}



}
