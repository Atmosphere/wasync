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
package org.atmosphere.wasync.serial;

import com.google.common.util.concurrent.SettableFuture;
import com.ning.http.client.Response;
import org.atmosphere.wasync.Socket;

/**
 * Interface for implementing serialized/ordered {@link Socket#fire(Object)}.
 *
 * @author Christian Bach
 */
public interface SerializedFireStage {

	public void setSocket(SerializedSocket socket);
	
	public void enqueue(Object firePayload, SettableFuture<Response> originalFuture);
	
	public void shutdown();
	
}
