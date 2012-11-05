/*
 * Copyright 2012 Jeanfrancois Arcand
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
package org.atmosphere.wasync;

/**
 * FunctionResolver are useful for mapping received message with a {@link Function}. By default, only the predefined
 * function {@link Function.MESSAGE} are automatically mapped to Function. An application can define its own
 * Function.MESSAGE be writing the appropriate FunctionWrapper.
 *
 * By default, the {@link org.atmosphere.wasync.impl.DefaultFunctionResolver} is used.
 *
 * @author Jeanfrancois Arcand
 */
public interface FunctionResolver {
    /**
     * Resolve the current message with
     * @param message the original response's body received
     * @param functionName the default function name taken from {@link Function.MESSAGE}
     * @param fn The current {@link FunctionWrapper}
     * @return true if the {@link Function} can be invoked.
     */
    boolean resolve(String message, Object functionName, FunctionWrapper fn);
}
