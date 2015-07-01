package uk.co.o2wifi.modjradius.impl;

import net.jradius.client.auth.RadiusAuthenticator;
import net.jradius.exception.RadiusException;
import net.jradius.packet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.datagram.InternetProtocolFamily;
import uk.co.o2wifi.modjradius.RadiusClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

/**
 * Created by pete on 22/06/15.
 */
public class DefaultRadiusClient implements RadiusClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultRadiusClient.class);
    private static final RadiusFormat format = RadiusFormat.getInstance();
    private Vertx vertx;
    private final DatagramSocket datagramSocket;

    private int acctPort;
    private int authPort;
    private String sharedSecret;
    private String host;

    public DefaultRadiusClient(Vertx vertx, InternetProtocolFamily internetProtocolFamily) {
        this.vertx = vertx;
        this.datagramSocket = vertx.createDatagramSocket(internetProtocolFamily);
    }

    public DefaultRadiusClient(Vertx vertx) {
        this.vertx = vertx;
        this.datagramSocket = vertx.createDatagramSocket(InternetProtocolFamily.IPv4);
    }

    @Override
    public void close() {
        if (datagramSocket != null) {
            datagramSocket.close();
        }
    }

    @Override
    public void send(RadiusRequest request, Handler<AsyncResult<DatagramSocket>> handler) throws IOException {
        int port = request instanceof AccountingRequest ? acctPort : authPort;

        ByteBuffer byteBuffer = ByteBuffer.allocate(4096);
        format.packPacket(request, sharedSecret, byteBuffer, true);

        Buffer buffer = new Buffer(byteBuffer.array());

        datagramSocket.send(buffer, host, port, handler);
    }

    @Override
    public void sendReceive(RadiusRequest request, Handler<AsyncResult<RadiusResponse>> handler) throws IOException {
        int port = request instanceof AccountingRequest ? acctPort : authPort;

        ByteBuffer byteBuffer = ByteBuffer.allocate(4096);
        format.packPacket(request, sharedSecret, byteBuffer, true);

        Buffer buffer = new Buffer(byteBuffer.array());

        datagramSocket.send(buffer, host, port, event -> event.result().dataHandler(packet -> {
                    final RadiusPacket replyPacket;
                    try {
                        ByteBuffer replyByteBuffer = ByteBuffer.wrap(packet.data().getBytes());
                        replyPacket = PacketFactory.parseUDP(replyByteBuffer);
                        if (!(replyPacket instanceof RadiusResponse)) {
                            handler.handle(new AsyncResult<RadiusResponse>() {
                                @Override
                                public RadiusResponse result() {
                                    return (RadiusResponse) replyPacket;
                                }

                                @Override
                                public Throwable cause() {
                                    return new RadiusException("Received something other than a RADIUS Response to a Request");
                                }

                                @Override
                                public boolean succeeded() {
                                    return false;
                                }

                                @Override
                                public boolean failed() {
                                    return true;
                                }
                            });
                            return;
                        }
                    } catch (RadiusException | IOException e) {
                        handler.handle(new AsyncResult<RadiusResponse>() {
                            @Override
                            public RadiusResponse result() {
                                return null;
                            }

                            @Override
                            public Throwable cause() {
                                return new RadiusException("Received something other than a RADIUS Response to a Request");
                            }

                            @Override
                            public boolean succeeded() {
                                return false;
                            }

                            @Override
                            public boolean failed() {
                                return true;
                            }
                        });
                        return;
                    }

                    handler.handle(new AsyncResult<RadiusResponse>() {
                        @Override
                        public RadiusResponse result() {
                            return (RadiusResponse) replyPacket;
                        }

                        @Override
                        public Throwable cause() {
                            return null;
                        }

                        @Override
                        public boolean succeeded() {
                            return true;
                        }

                        @Override
                        public boolean failed() {
                            return false;
                        }
                    });
                })
        );
    }

    @Override
    public void authenticate(AccessRequest p, final RadiusAuthenticator auth, Handler<AsyncResult<RadiusResponse>> handler) throws RadiusException, NoSuchAlgorithmException, IOException {
        auth.setupRequest(null, p);
        auth.processRequest(p);

        while (true) {
            sendReceive(p, event -> {
                if (event.succeeded() && event.result() instanceof AccessChallenge) {
                    try {
                        auth.processChallenge(p, event.result());
                    } catch (RadiusException e) {
                        handler.handle(new AsyncResult<RadiusResponse>() {
                            @Override
                            public RadiusResponse result() {
                                return event.result();
                            }

                            @Override
                            public Throwable cause() {
                                return e;
                            }

                            @Override
                            public boolean succeeded() {
                                return false;
                            }

                            @Override
                            public boolean failed() {
                                return true;
                            }
                        });
                    }
                } else {
                    handler.handle(new AsyncResult<RadiusResponse>() {
                        @Override
                        public RadiusResponse result() {
                            return event.result();
                        }

                        @Override
                        public Throwable cause() {
                            return null;
                        }

                        @Override
                        public boolean succeeded() {
                            return true;
                        }

                        @Override
                        public boolean failed() {
                            return false;
                        }
                    });
                }
            });
        }
    }

    @Override
    public void accounting(AccountingRequest p, Handler<AsyncResult<RadiusResponse>> handler) throws RadiusException, IOException {
        sendReceive(p, event -> {
            if (event.succeeded() && !(event.result() instanceof AccountingResponse)) {
                handler.handle(new AsyncResult<RadiusResponse>() {
                    @Override
                    public RadiusResponse result() {
                        return event.result();
                    }

                    @Override
                    public Throwable cause() {
                        return new RadiusException("Received something other than a RADIUS Response to a Request");
                    }

                    @Override
                    public boolean succeeded() {
                        return false;
                    }

                    @Override
                    public boolean failed() {
                        return true;
                    }
                });

                return;
            }

            handler.handle(event);
        });
    }

    @Override
    public void disconnect(DisconnectRequest p, Handler<AsyncResult<RadiusResponse>> handler) throws RadiusException, IOException {
        sendReceive(p, event -> {
            if (event.succeeded() && !(event.result() instanceof DisconnectResponse)) {
                handler.handle(new AsyncResult<RadiusResponse>() {
                    @Override
                    public RadiusResponse result() {
                        return event.result();
                    }

                    @Override
                    public Throwable cause() {
                        return new RadiusException("Received something other than a RADIUS Response to a Request");
                    }

                    @Override
                    public boolean succeeded() {
                        return false;
                    }

                    @Override
                    public boolean failed() {
                        return true;
                    }
                });

                return;
            }

            handler.handle(event);
        });
    }

    @Override
    public void changeOfAuth(CoARequest p, Handler<AsyncResult<RadiusResponse>> handler) throws RadiusException, IOException {
        sendReceive(p, event -> {
            if (event.succeeded() && !(event.result() instanceof CoAResponse)) {
                handler.handle(new AsyncResult<RadiusResponse>() {
                    @Override
                    public RadiusResponse result() {
                        return event.result();
                    }

                    @Override
                    public Throwable cause() {
                        return new RadiusException("Received something other than a RADIUS Response to a Request");
                    }

                    @Override
                    public boolean succeeded() {
                        return false;
                    }

                    @Override
                    public boolean failed() {
                        return true;
                    }
                });

                return;
            }

            handler.handle(event);
        });
    }

    @Override
    public int getAcctPort() {
        return acctPort;
    }

    @Override
    public void setAcctPort(int acctPort) {
        this.acctPort = acctPort;
    }

    @Override
    public int getAuthPort() {
        return authPort;
    }

    @Override
    public void setAuthPort(int authPort) {
        this.authPort = authPort;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public void setHost(String hosts) {
        this.host = hosts;
    }


    @Override
    public String getSharedSecret() {
        return sharedSecret;
    }

    @Override
    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }
}
