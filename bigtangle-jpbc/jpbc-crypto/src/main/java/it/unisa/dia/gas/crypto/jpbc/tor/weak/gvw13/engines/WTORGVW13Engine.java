package it.unisa.dia.gas.crypto.jpbc.tor.weak.gvw13.engines;

import it.unisa.dia.gas.crypto.cipher.AbstractElementCipher;
import it.unisa.dia.gas.crypto.cipher.ElementCipher;
import it.unisa.dia.gas.crypto.jpbc.tor.weak.gvw13.params.WTORGVW13KeyParameters;
import it.unisa.dia.gas.crypto.jpbc.tor.weak.gvw13.params.WTORGVW13PublicKeyParameters;
import it.unisa.dia.gas.crypto.jpbc.tor.weak.gvw13.params.WTORGVW13RecodeParameters;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import org.bouncycastle.crypto.CipherParameters;

/**
 * @author Angelo De Caro (jpbclib@gmail.com)
 * @since 2.0.0
 */
public class WTORGVW13Engine extends AbstractElementCipher {

    private CipherParameters param;
    private Pairing pairing;

    public ElementCipher init(CipherParameters param) {
        this.param = param;

        WTORGVW13KeyParameters keyParameters = (WTORGVW13KeyParameters) param;
        pairing = PairingFactory.getPairing(keyParameters.getParameters().getParameters());

        return this;
    }


    public Element processElements(Element... input) {
        if (param instanceof WTORGVW13PublicKeyParameters) {
            WTORGVW13PublicKeyParameters keyParameters = (WTORGVW13PublicKeyParameters) param;

            // Read Input
            Element s = input[0];

            // Encode
            Element result;
            if (keyParameters.getLevel() == 0) {
                result = keyParameters.getLeft().powZn(s);
            } else {
                result = pairing.pairing(
                        keyParameters.getParameters().getG1a(),
                        keyParameters.getRight()
                ).powZn(s);
            }

            return result;
        } else {
            WTORGVW13RecodeParameters keyParameters = (WTORGVW13RecodeParameters) param;

            // Read Input
            Element c0 = input[0];
            Element c1 = input[1];

            // Recode
            return pairing.pairing(c0, keyParameters.getRk()).mul(c1);
       }

    }
}
