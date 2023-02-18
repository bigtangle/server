package net.bigtangle.script;

import static org.junit.Assert.assertEquals;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.bouncycastle.util.encoders.Hex;

import net.thiim.dilithium.impl.DilithiumPrivateKeyImpl;
import net.thiim.dilithium.impl.PackingUtils;
import net.thiim.dilithium.interfaces.DilithiumParameterSpec;
import net.thiim.dilithium.interfaces.DilithiumPrivateKey;
import net.thiim.dilithium.interfaces.DilithiumPrivateKeySpec;
import net.thiim.dilithium.interfaces.DilithiumPublicKey;
import net.thiim.dilithium.interfaces.DilithiumPublicKeySpec;
import net.thiim.dilithium.provider.DilithiumProvider;

public class Test {
    @org.junit.Test
    public void test() throws Exception {
        DilithiumProvider pv = new DilithiumProvider();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Dilithium", pv);
        kpg.initialize(DilithiumParameterSpec.LEVEL3);
        KeyPair kp = kpg.generateKeyPair();

        DilithiumPrivateKey sk = (DilithiumPrivateKey) kp.getPrivate();
        DilithiumPublicKey pk = (DilithiumPublicKey) kp.getPublic();
        System.out.println("pk = " + Hex.toHexString(pk.getEncoded()).toUpperCase());
        System.out.println("sk = " + Hex.toHexString(sk.getEncoded()).toUpperCase());

        DilithiumPublicKeySpec pubspec = new DilithiumPublicKeySpec(DilithiumParameterSpec.LEVEL3, pk.getEncoded());
        DilithiumPublicKey publicKey =(DilithiumPublicKey) PackingUtils.unpackPublicKey(pubspec.getParameterSpec(), pubspec.getBytes());
        System.out.println("pk1 = " + Hex.toHexString(publicKey.getEncoded()).toUpperCase());

        DilithiumPrivateKeySpec prvspec = new DilithiumPrivateKeySpec(DilithiumParameterSpec.LEVEL3, sk.getEncoded());
        DilithiumPrivateKey privateKey = (DilithiumPrivateKey) PackingUtils.unpackPrivateKey(prvspec.getParameterSpec(), prvspec.getBytes());
        System.out.println("sk2 = " + Hex.toHexString(privateKey.getEncoded()).toUpperCase());
        assertEquals(sk.getEncoded(), privateKey.getEncoded());assertEquals(pk.getEncoded(), publicKey.getEncoded());
    }
}
