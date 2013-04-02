package org.atmosphere.wasync;

import org.atmosphere.wasync.impl.AtmosphereClient;
import org.codehaus.jackson.map.ObjectMapper;
import org.nettosphere.samples.chat.Chat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

public class wAsyncChat {

    private final static Logger logger = LoggerFactory.getLogger(wAsyncChat.class);
    private final static ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            args = new String[] {"http://127.0.0.1:8080"};
        }

        Options options = new Options.OptionsBuilder().build();
        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(args[0] + "/chat")
                .trackMessageLength(true)
                .encoder(new Encoder<String, Chat.Data>() {
                    @Override
                    public Chat.Data encode(String s) {
                        try {
                            return mapper.readValue(s, Chat.Data.class);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                })
                .encoder(new Encoder<Chat.Data, String>() {
                    @Override
                    public String encode(Chat.Data data) {
                        try {
                            return mapper.writeValueAsString(data);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                })
                .decoder(new Decoder<String, Chat.Data>() {
                    @Override
                    public Chat.Data decode(Transport.EVENT_TYPE type, String data) {

                        data = data.trim();

                        // Padding
                        if (data.length() == 0) {
                            return null;
                        }

                        if (type.equals(Transport.EVENT_TYPE.MESSAGE)) {
                            try {
                                return mapper.readValue(data, Chat.Data.class);
                            } catch (IOException e) {
                                logger.debug("Invalid message {}", data);
                                return null;
                            }
                        } else {
                            return null;
                        }
                    }
                })
                .transport(Request.TRANSPORT.WEBSOCKET)
                .transport(Request.TRANSPORT.LONG_POLLING);

        Socket socket = client.create(options);
        socket.on("message", new Function<Chat.Data>() {
            @Override
            public void on(Chat.Data t) {
                Date d = new Date(t.getTime()) ;
                logger.info("Author {}: {}", t.getAuthor() + "@ " + d.getHours() + ":" + d.getMinutes(), t.getMessage());
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
            }

        }).open(request.build());

        logger.info("Choose Name: ");
        String name = null;
        String a = "";
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (!(a.equals("quit"))) {
            a = br.readLine();
            if (name == null) {
                name = a;
            }
            socket.fire(new Chat.Data(name, a));
        }
        socket.close();
    }


}

