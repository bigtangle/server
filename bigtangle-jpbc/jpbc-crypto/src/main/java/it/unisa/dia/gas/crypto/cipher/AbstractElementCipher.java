package it.unisa.dia.gas.crypto.cipher;

import it.unisa.dia.gas.jpbc.Element;
import org.bouncycastle.crypto.CipherParameters;

/**
 * @author Angelo De Caro (jpbclib@gmail.com)
 */
public class AbstractElementCipher implements ElementCipher {

    public ElementCipher init(CipherParameters param) {
        throw new IllegalStateException("Not implemented yet!!!");
    }

    public ElementCipher init(Element key) {
        throw new IllegalStateException("Not implemented yet!!!");
    }

    public Element processBytes(byte[] buffer) {
        throw new IllegalStateException("Not implemented yet!!!");
    }

    public Element processElements(Element... input) {
        throw new IllegalStateException("Not implemented yet!!!");
    }

    public byte[] processElementsToBytes(Element... input) {
        throw new IllegalStateException("Not implemented yet!!!");
    }
}
