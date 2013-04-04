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
package org.atmosphere.wasync.andoid;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.impl.AtmosphereClient;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.Date;

public class wAsyncChat extends Activity {
    private Button bt;
    private TextView tv;
    private String serverIpAddress = "http://10.0.2.2:8080";
    private final static ObjectMapper mapper = new ObjectMapper();
    private final Handler uiHandler = new Handler();
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.main);
        bt = (Button) findViewById(R.id.myButton);
        tv = (TextView) findViewById(R.id.myTextView);

        try {
            Options options = new Options.OptionsBuilder().build();
            AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

            RequestBuilder request = client.newRequestBuilder()
                    .method(Request.METHOD.GET)
                    .uri(serverIpAddress + "/chat")
                    .trackMessageLength(true)
                    .encoder(new Encoder<Data, String>() {
                        @Override
                        public String encode(Data data) {
                            try {
                                return mapper.writeValueAsString(data);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    })
                    .decoder(new Decoder<String, Data>() {
                        @Override
                        public Data decode(Transport.EVENT_TYPE type, String data) {

                            data = data.trim();

                            // Padding
                            if (data.length() == 0) {
                                return null;
                            }

                            if (type.equals(Transport.EVENT_TYPE.MESSAGE)) {
                                try {
                                    return mapper.readValue(data, Data.class);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return null;
                                }
                            } else {
                                return null;
                            }
                        }
                    })
                    .transport(Request.TRANSPORT.WEBSOCKET);

            final org.atmosphere.wasync.Socket socket = client.create(options);
            socket.on("message", new Function<Data>() {
                @Override
                public void on(final Data t) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Date d = new Date(t.getTime());
                            tv.append("Author " + t.getAuthor() + "@ " + d.getHours() + ":" + d.getMinutes() + ": " + t.getMessage() + "\n");
                        }
                    });
                }
            }).on(new Function<Throwable>() {

                @Override
                public void on(Throwable t) {
                    tv.setText("ERROR 3: " + t.getMessage());
                    t.printStackTrace();
                }

        }).open(request.build());

            bt.setOnClickListener(new OnClickListener() {

                String name = null;

                public void onClick(View v) {
                    try {
                        EditText et = (EditText) findViewById(R.id.EditText01);
                        String str = et.getText().toString();
                        if (name == null) {
                            name = str;
                        }
                        socket.fire(new Data(name, str));
                        et.setText("");
                        Log.d("Client", "Client sent message");
                    } catch (Throwable e) {
                        tv.setText("ERROR 3: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });

        } catch (Throwable e) {
            tv.setText("Unable to connect: " + e.getMessage());

            e.printStackTrace();
        }
    }
}
