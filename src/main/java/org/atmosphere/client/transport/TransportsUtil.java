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
package org.atmosphere.client.transport;

import org.atmosphere.client.Decoder;
import org.atmosphere.client.Function;
import org.atmosphere.client.FunctionWrapper;
import org.atmosphere.client.util.TypeResolver;

import java.util.List;

public class TransportsUtil {

    static void invokeFunction(List<Decoder<? extends Object, ?>> decoders, List<FunctionWrapper> functions, Class<?> implementedType, Object instanceType, String functionName) {
        for (FunctionWrapper wrapper : functions) {
            Function f = wrapper.function();
            String fn = wrapper.functionName();
            Class<?>[] typeArguments = TypeResolver.resolveArguments(f.getClass(), Function.class);

            if (typeArguments.length > 0) {
                instanceType = matchDecoder(instanceType, decoders);
                implementedType = instanceType.getClass();
            }

            if (typeArguments.length > 0 && typeArguments[0].equals(implementedType)) {
                if (fn.isEmpty() || fn.equalsIgnoreCase(functionName)) {
                    f.on(instanceType);
                }
            }
        }
    }

    static Object matchDecoder(Object instanceType, List<Decoder<? extends Object, ?>> decoders) {
        for (Decoder d : decoders) {
            Class<?>[] typeArguments = TypeResolver.resolveArguments(d.getClass(), Decoder.class);
            if (typeArguments.length > 0 && typeArguments[0].equals(instanceType.getClass())) {
                instanceType = d.decode(instanceType);
            }
        }
        return instanceType;
    }
}
