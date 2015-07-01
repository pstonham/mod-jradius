package uk.co.o2wifi.modjradius;

import net.jradius.client.auth.RadiusAuthenticator;
import net.jradius.exception.RadiusException;
import net.jradius.exception.UnknownAttributeException;
import net.jradius.packet.*;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.datagram.DatagramSocket;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * Created by pete on 22/06/15.
 */
public interface RadiusClient {
    void close();

    void send(RadiusRequest p, Handler<AsyncResult<DatagramSocket>> handler) throws Exception;

    void sendReceive(RadiusRequest request, Handler<AsyncResult<RadiusResponse>> handler) throws IOException;

    void authenticate(AccessRequest p, RadiusAuthenticator auth, Handler<AsyncResult<RadiusResponse>> handler)
            throws RadiusException, NoSuchAlgorithmException, IOException;

    void accounting(AccountingRequest p, Handler<AsyncResult<RadiusResponse>> handler)
            throws RadiusException, IOException;

    void disconnect(DisconnectRequest p, Handler<AsyncResult<RadiusResponse>> handler)
            throws RadiusException, IOException;

    void changeOfAuth(CoARequest p, Handler<AsyncResult<RadiusResponse>> handler)
            throws RadiusException, IOException;

    int getAcctPort();

    void setAcctPort(int acctPort);

    int getAuthPort();

    void setAuthPort(int authPort);

    void setHost(String host);

    String getHost();

    String getSharedSecret();

    void setSharedSecret(String sharedSecret);
}
