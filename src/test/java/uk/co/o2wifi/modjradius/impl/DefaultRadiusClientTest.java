package uk.co.o2wifi.modjradius.impl;

import net.jradius.dictionary.Attr_UserName;
import net.jradius.dictionary.Attr_UserPassword;
import net.jradius.dictionary.vsa_cisco.Attr_CiscoAVPair;
import net.jradius.dictionary.vsa_cisco.Attr_CiscoAccountInfo;
import net.jradius.packet.*;
import net.jradius.packet.attribute.AttributeList;
import org.junit.Test;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.datagram.InternetProtocolFamily;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;
import uk.co.o2wifi.modjradius.RadiusClient;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;


/**
 * Created by pete on 23/06/15.
 */
public class DefaultRadiusClientTest extends TestVerticle {
    private volatile DatagramSocket peer1;

    @Test
    public void givenARequest_whenSentViaSend_thenRequestReceived() throws Exception {
        peer1 = vertx.createDatagramSocket(InternetProtocolFamily.IPv4);

        final RadiusClient radiusClient = new DefaultRadiusClient(vertx, InternetProtocolFamily.IPv4);

        radiusClient.setAcctPort(12398);
        radiusClient.setAuthPort(12398);
        radiusClient.setHost("127.0.0.1");

        peer1.listen("127.0.0.1", 12398, event -> {
            VertxAssert.assertTrue("Failed to setup test listener: " + event.cause(), event.succeeded());

            AttributeList attrs = new AttributeList();
            RadiusRequest request = new AccessRequest(null, attrs);

            peer1.dataHandler(event2 -> VertxAssert.assertTrue(event2.data() != null));

            try {
                radiusClient.send(request, event1 -> VertxAssert.assertTrue("Sending failed", event1.succeeded()));
            } catch (Exception e) {
                VertxAssert.fail(e.getMessage());
            }
            VertxAssert.testComplete();
        });
    }

    @Test
    public void givenARequest_whenSentViaSendReceive_thenResponseReceived() throws Exception {
        peer1 = vertx.createDatagramSocket(InternetProtocolFamily.IPv4);

        final RadiusClient radiusClient = new DefaultRadiusClient(vertx, InternetProtocolFamily.IPv4);

        radiusClient.setAcctPort(12398);
        radiusClient.setAuthPort(12398);
        radiusClient.setHost("127.0.0.1");
        radiusClient.setSharedSecret("somesecret");

        peer1.listen("127.0.0.1", 12398, event -> {
            VertxAssert.assertTrue("Failed to setup test listener: " + event.cause(), event.succeeded());

            RadiusRequest request = new CoARequest();
            request.addAttribute(new Attr_CiscoAVPair("subscriber:command=account-logon"));
            request.addAttribute(new Attr_UserName("aa:aa:aa:aa:aa"));
            request.addAttribute(new Attr_UserPassword(""));
            request.addAttribute(new Attr_CiscoAccountInfo("172.16.10.65"));

            peer1.dataHandler(datagramPacket -> {
                byte[] requestBytes = datagramPacket.data().getBytes();
                DatagramPacket requestPacket = new DatagramPacket(requestBytes, requestBytes.length);
                CoARequest requestRadiusPacket = null;
                try {
                    requestRadiusPacket = (CoARequest) PacketFactory.parse(requestPacket);

                    VertxAssert.assertEquals("Wrong number of attributes received", 4, requestRadiusPacket.getAttributes().getAttributeList().size());

                    RadiusResponse radiusResponse = new CoAACK();
                    radiusResponse.addAttribute(new Attr_UserName("aa:aa:aa:aa:aa"));

                    ByteBuffer byteBuffer = ByteBuffer.allocate(4096);
                    RadiusFormat.getInstance().packPacket(radiusResponse, "somesecret", byteBuffer, true);

                    Buffer buffer = new Buffer(byteBuffer.array());
                    peer1.send(buffer, datagramPacket.sender().getHostName(), datagramPacket.sender().getPort(), event1 -> {
                    });
                } catch (Exception e) {
                    VertxAssert.fail("Error reading packet " + e);
                }

            });

            try {
                radiusClient.sendReceive(request, response -> {
                    try {
                        VertxAssert.assertTrue("Sending failed:" + response.cause(), response.succeeded());
                        VertxAssert.assertEquals("Incorrect response", 44, response.result().getCode());
                        VertxAssert.assertEquals("Incorrect username", "aa:aa:aa:aa:aa", new String(response.result().findAttribute(Attr_UserName.TYPE).getValue().getBytes()));
                    } catch (Exception e) {
                        VertxAssert.fail("Error reading packet " + e);
                    }
                    VertxAssert.testComplete();
                });
            } catch (Exception e) {
                VertxAssert.fail(e.getMessage());
            }
        });
    }
}
