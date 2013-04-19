package org.atmosphere.wasync.impl;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.atmosphere.wasync.ISerializedFireStage;


import com.google.common.util.concurrent.SettableFuture;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;

public class DefaultSerializedFireStage implements ISerializedFireStage {

	private volatile SequentialHTTPSocket socket;
	private final int maxBinaryMessagesAggregationSize;
	
	private final BlockingQueue<FirePayloadEntry> firePayloadsQueue;
	private final ExecutorService executorService; 
	private final Runnable fireTask;	 
	
	public DefaultSerializedFireStage() {
		this(4);		
	}
	
	public DefaultSerializedFireStage(int maxBinaryPayloadAggregationSize) {
		this.maxBinaryMessagesAggregationSize = maxBinaryPayloadAggregationSize;
		firePayloadsQueue = new LinkedBlockingQueue<FirePayloadEntry>();
		executorService = Executors.newSingleThreadExecutor();
		fireTask = createFireTask();
		executorService.execute(fireTask);
	}
	
	@Override
	public void setSocket(SequentialHTTPSocket socket) {
		this.socket = socket;
	}
	
	@Override
	public void enqueue(Object firePayload, SettableFuture<Response> originalFuture) {
		firePayloadsQueue.add(new FirePayloadEntry(firePayload, originalFuture));
	}
	
	private Runnable createFireTask() {
		return new Runnable() {
			public void run() {
				ArrayList<FirePayloadEntry> aggregatedByteArrayPayloads = new ArrayList<FirePayloadEntry>(maxBinaryMessagesAggregationSize);				
				try {
					while(!Thread.currentThread().isInterrupted()) {
						FirePayloadEntry payloadEntry = firePayloadsQueue.take();						
						int aggregationCount = 0;
						
						for(; aggregationCount < maxBinaryMessagesAggregationSize; aggregationCount++) {
							if (byte[].class.isAssignableFrom(payloadEntry.getFirePayload().getClass())) {
								aggregatedByteArrayPayloads.add(payloadEntry);
								payloadEntry = firePayloadsQueue.poll();
								if (payloadEntry == null) {
									aggregationCount = 0;
									break;
								}
							} else {
								if (!aggregatedByteArrayPayloads.isEmpty()) {
									fireSynchronously(aggregatedByteArrayPayloads);
									aggregatedByteArrayPayloads.clear();
								} 
								fireSynchronously(payloadEntry);
								aggregationCount = 0;
								break;
							}
						}
						
						if(!aggregatedByteArrayPayloads.isEmpty()) {
							fireSynchronously(aggregatedByteArrayPayloads);
							aggregatedByteArrayPayloads.clear();					
						}
							
					}
				}catch (InterruptedException consumed) {
					// allow thread to exit
				}
			}
		};
	}
	
	private void fireSynchronously(ArrayList<FirePayloadEntry> aggregatedByteArrayPayloads) {
		ListenableFuture<Response> future;
		int aggregatedSize = 0;
		for (FirePayloadEntry entry : aggregatedByteArrayPayloads) {
			aggregatedSize += ((byte[])entry.getFirePayload()).length;
		}
		byte[] aggregatedByteArray = new byte[aggregatedSize];
		int destPos = 0;
		for (FirePayloadEntry entry : aggregatedByteArrayPayloads) {
			byte[] payload = (byte[])entry.getFirePayload();
			System.arraycopy(
					payload, 0, 
					aggregatedByteArray, destPos, 
					payload.length);
			destPos += payload.length;
		}
		
	
		Response response = null;
		try {
			future = socket.directWrite(aggregatedByteArray);
			response = future.get();
		} catch (Exception e) {				
			for (FirePayloadEntry entry : aggregatedByteArrayPayloads) {
				entry.getOriginalFuture().setException(e);
				entry.getOriginalFuture().cancel(true);
			}
		} finally {
			for (FirePayloadEntry entry : aggregatedByteArrayPayloads) {
				entry.getOriginalFuture().set(response);
			}
		}								
	}
	
	public void fireSynchronously(FirePayloadEntry firePayloadEntry) {
		ListenableFuture<Response> future;
		Response response = null;
		try {
			future = socket.directWrite(firePayloadEntry);
			response = future.get();
		} catch (Exception e) {				
			firePayloadEntry.getOriginalFuture().setException(e);
			firePayloadEntry.getOriginalFuture().cancel(true);
		} finally {			
			firePayloadEntry.getOriginalFuture().set(response);
		}
	}
	
	public void shutdown() {
		executorService.shutdownNow();
		for (FirePayloadEntry entry  : firePayloadsQueue) {
			entry.getOriginalFuture().cancel(true);
		}
	} 
	
	private class FirePayloadEntry {
		
		private Object firePayload;
		private SettableFuture<Response> originalFuture;
		
		public FirePayloadEntry(Object firePayload, SettableFuture<Response> originalFuture) {
			this.firePayload = firePayload;
			this.originalFuture = originalFuture;
		}
		
		public Object getFirePayload() { 
			return firePayload;
		}
		
		public SettableFuture<Response> getOriginalFuture() { 
			return originalFuture;
		}
		
	}
	
}
