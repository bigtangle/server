import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import au.edu.unimelb.cs.culnane.crypto.bls.BLSPrivateKey;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSPublicKey;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSSignature;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSSystemParameters;
import au.edu.unimelb.cs.culnane.crypto.exceptions.BLSSignatureException;
import au.edu.unimelb.cs.culnane.utils.IOUtils;
import au.edu.unimelb.cs.culnane.utils.IOUtils.EncodingType;
import it.unisa.dia.gas.jpbc.Element;

public class TestBLSSign {

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, BLSSignatureException {
        BLSSystemParameters sysParams = new BLSSystemParameters("./params.json");
        byte[] priv = IOUtils.decodeData(EncodingType.BASE64, args[0]);
        BLSPrivateKey privKey = new BLSPrivateKey(sysParams.getPairing().getZr().newElement(new BigInteger(priv)));
        Element publicKey = sysParams.getPairing().getG2().newElement();


        // Set the element value from the bytes decoded from the JSON
        publicKey.setFromBytes(IOUtils.decodeData(EncodingType.BASE64, args[1]));
        BLSPublicKey pub = new BLSPublicKey(publicKey);
    
        BLSSignature b = new BLSSignature( "SHA-256", sysParams);
        String data = "this is test";
        b.update(data);
        b.initVerify(pub);
        b.initSign(privKey); 
    
        assertTrue(b.verify(b.sign().toBytes()));
    }

}
