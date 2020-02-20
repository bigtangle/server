package it.unisa.dia.gas.crypto.circuit;

import it.unisa.dia.gas.jpbc.Element;

/**
 * @author Angelo De Caro (jpbclib@gmail.com)
 */
public interface ArithmeticGate extends Gate<Element> {

    Element getAlphaAt(int index);

}
