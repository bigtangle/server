/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.crypto;

import org.junit.Test;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertEquals;

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
