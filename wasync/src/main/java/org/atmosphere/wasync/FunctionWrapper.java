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
package org.atmosphere.wasync;

/**
 * A tuple which contains a {@link Function} and its associated functionName. The values are passed from
 * <blockquote><pre>
 *
 *     socket.on("message, new Function&lt;String&gt;() {
 *         ....
 *     }
 * </pre></blockquote>
 * This class is only used by {@link Transport} implementation.
 *
 * @author Jeanfrancois Arcand
 */
public class FunctionWrapper {

    private final String functionName;
    private final Function<?> function;

    public FunctionWrapper(String functionName, Function<?> function) {
        this.functionName = functionName;
        this.function = function;
    }

    public Function<?> function(){
        return function;
    }

    public String functionName() {
        return functionName;
    }
}
