/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import net.bigtangle.core.Utils;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.wallet.Protos;
import net.bigtangle.wallet.Protos.ScryptParameters;

public class KeyCrypterScryptTest {

    private static final Logger log = LoggerFactory.getLogger(KeyCrypterScryptTest.class);

    // Nonsense bytes for encryption test.
    private static final byte[] TEST_BYTES1 = {0, -101, 2, 103, -4, 105, 6, 107, 8, -109, 10, 111, -12, 113, 14, -115, 16, 117, -18, 119, 20, 121, 22, 123, -24, 125, 26, 127, -28, 29, -30, 31};

    private static final CharSequence PASSWORD1 = "aTestPassword";
    private static final CharSequence PASSWORD2 = "0123456789";

    private static final CharSequence WRONG_PASSWORD = "thisIsTheWrongPassword";

    private ScryptParameters scryptParameters;

    @BeforeEach
    public void setUp() throws Exception {
        Protos.ScryptParameters.Builder scryptParametersBuilder = Protos.ScryptParameters.newBuilder()
                .setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt()));
        scryptParameters = scryptParametersBuilder.build();

        BriefLogFormatter.init();
    }

    @Test
    public void testKeyCrypterGood1() throws KeyCrypterException {
        KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt(scryptParameters);

        // Encrypt.
        EncryptedData data = keyCrypter.encrypt(TEST_BYTES1, keyCrypter.deriveKey(PASSWORD1));
        assertNotNull(data);

        // Decrypt.
        byte[] reborn = keyCrypter.decrypt(data, keyCrypter.deriveKey(PASSWORD1));
        log.debug("Original: " + Utils.HEX.encode(TEST_BYTES1));
        log.debug("Reborn  : " + Utils.HEX.encode(reborn));
        assertEquals(Utils.HEX.encode(TEST_BYTES1), Utils.HEX.encode(reborn));
    }

    /**
     * Test with random plain text strings and random passwords.
     * UUIDs are used and hence will only cover hex characters (and the separator hyphen).
     * @throws KeyCrypterException
     */
    @Test
    public void testKeyCrypterGood2() {
        KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt(scryptParameters);

        // Trying random UUIDs for plainText and passwords.
        int numberOfTests = 16;
        for (int i = 0; i < numberOfTests; i++) {
            // Create a UUID as the plaintext and use another for the password.
            String plainText = UUID.randomUUID().toString();
            CharSequence password = UUID.randomUUID().toString();

            EncryptedData data = keyCrypter.encrypt(plainText.getBytes(), keyCrypter.deriveKey(password));

            assertNotNull(data);

            byte[] reconstructedPlainBytes = keyCrypter.decrypt(data,keyCrypter.deriveKey(password));
            assertEquals(Utils.HEX.encode(plainText.getBytes()), Utils.HEX.encode(reconstructedPlainBytes));
        }
    }

    @Test
    public void testKeyCrypterWrongPassword() throws KeyCrypterException {
        KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt(scryptParameters);

        // create a longer encryption string
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append(i).append(" The quick brown fox");
        }

        EncryptedData data = keyCrypter.encrypt(builder.toString().getBytes(), keyCrypter.deriveKey(PASSWORD2));
        assertNotNull(data);

        try {
            keyCrypter.decrypt(data, keyCrypter.deriveKey(WRONG_PASSWORD));
            // TODO: This test sometimes fails due to relying on padding.
            fail("Decrypt with wrong password did not throw exception");
        } catch (KeyCrypterException ede) {
            assertTrue(ede.getMessage().contains("Could not decrypt"));
        }
    }

    @Test
    public void testEncryptDecryptBytes1() throws KeyCrypterException {
        KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt(scryptParameters);

        // Encrypt bytes.
        EncryptedData data = keyCrypter.encrypt(TEST_BYTES1, keyCrypter.deriveKey(PASSWORD1));
        assertNotNull(data);
        log.debug("\nEncrypterDecrypterTest: cipherBytes = \nlength = " + data.encryptedBytes.length + "\n---------------\n" + Utils.HEX.encode(data.encryptedBytes) + "\n---------------\n");

        byte[] rebornPlainBytes = keyCrypter.decrypt(data, keyCrypter.deriveKey(PASSWORD1));

        log.debug("Original: " + Utils.HEX.encode(TEST_BYTES1));
        log.debug("Reborn1 : " + Utils.HEX.encode(rebornPlainBytes));
        assertEquals(Utils.HEX.encode(TEST_BYTES1), Utils.HEX.encode(rebornPlainBytes));
    }

    @Test
    public void testEncryptDecryptBytes2() throws KeyCrypterException {
        KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt(scryptParameters);

        // Encrypt random bytes of various lengths up to length 50.
        Random random = new Random();

        for (int i = 0; i < 50; i++) {
            byte[] plainBytes = new byte[i];
            random.nextBytes(plainBytes);

            EncryptedData data = keyCrypter.encrypt(plainBytes, keyCrypter.deriveKey(PASSWORD1));
            assertNotNull(data);
            //log.debug("\nEncrypterDecrypterTest: cipherBytes = \nlength = " + cipherBytes.length + "\n---------------\n" + Utils.HEX.encode(cipherBytes) + "\n---------------\n");

            byte[] rebornPlainBytes = keyCrypter.decrypt(data, keyCrypter.deriveKey(PASSWORD1));

            log.debug("Original: (" + i + ") " + Utils.HEX.encode(plainBytes));
            log.debug("Reborn1 : (" + i + ") " + Utils.HEX.encode(rebornPlainBytes));
            assertEquals(Utils.HEX.encode(plainBytes), Utils.HEX.encode(rebornPlainBytes));
        }
    }
}
