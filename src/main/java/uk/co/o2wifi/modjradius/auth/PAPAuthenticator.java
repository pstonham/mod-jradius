package uk.co.o2wifi.modjradius.auth;

import net.jradius.client.auth.RadiusAuthenticator;
import net.jradius.exception.RadiusException;
import net.jradius.packet.RadiusPacket;
import net.jradius.packet.attribute.AttributeDictionary;
import net.jradius.packet.attribute.AttributeFactory;
import net.jradius.packet.attribute.RadiusAttribute;
import net.jradius.util.RadiusUtils;

import java.security.NoSuchAlgorithmException;

/**
 * Created by pete on 23/06/15.
 */
public class PAPAuthenticator extends RadiusAuthenticator {
    public static final String NAME = "pap";
    private String sharedSecret;

    @Override
    public String getAuthName() {
        return NAME;
    }

    @Override
    public void processRequest(RadiusPacket radiusPacket) throws RadiusException {
        if (password == null) throw new RadiusException("no password given");

        radiusPacket.removeAttribute(password);

        RadiusAttribute attr;
        radiusPacket.addAttribute(attr = AttributeFactory.newAttribute("User-Password"));
        attr.setValue(RadiusUtils.encodePapPassword(
                password.getValue().getBytes(),
                // Create an authenticator (AccessRequest just needs shared secret)
                radiusPacket.createAuthenticator(null, 0, 0, sharedSecret),
                sharedSecret));
    }

    public void setupRequest(RadiusPacket p, String sharedSecret) throws RadiusException, NoSuchAlgorithmException {
        RadiusAttribute a;
        this.sharedSecret = sharedSecret;

        if (username == null) {
            a = p.findAttribute(AttributeDictionary.USER_NAME);

            if (a == null)
                throw new RadiusException("You must at least have a User-Name attribute in a Access-Request");

            username = AttributeFactory.copyAttribute(a);
        }

        if (password == null) {
            a = p.findAttribute(AttributeDictionary.USER_PASSWORD);

            if (a != null) {
                password = AttributeFactory.copyAttribute(a);
            }
        }
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }
}
