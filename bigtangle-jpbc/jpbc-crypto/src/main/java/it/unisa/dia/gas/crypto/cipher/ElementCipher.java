package it.unisa.dia.gas.crypto.cipher;

import it.unisa.dia.gas.jpbc.Element;
import org.bouncycastle.crypto.CipherParameters;

/**
 * @author Angelo De Caro (jpbclib@gmail.com)
 */
public interface ElementCipher {

    public ElementCipher init(CipherParameters param);

    public ElementCipher init(Element key);


    public Element processBytes(byte[] buffer);

    public Element processElements(Element... input);

    public byte[] processElementsToBytes(Element... input);

}
