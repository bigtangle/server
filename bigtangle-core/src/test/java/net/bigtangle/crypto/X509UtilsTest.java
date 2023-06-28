/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.crypto;

import org.junit.Ignore;
import org.junit.jupiter.api.Test;

import net.bigtangle.crypto.X509Utils;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
@Ignore
public class X509UtilsTest {

    @Test
    public void testDisplayName() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        X509Certificate clientCert = (X509Certificate) cf.generateCertificate(getClass().getResourceAsStream(
                "startssl-client.crt"));
        assertEquals("Andreas Schildbach", X509Utils.getDisplayNameFromCertificate(clientCert, false));

        X509Certificate comodoCert = (X509Certificate) cf.generateCertificate(getClass().getResourceAsStream(
                "comodo-smime.crt"));
        assertEquals("comodo.com@schildbach.de", X509Utils.getDisplayNameFromCertificate(comodoCert, true));
    }
}
