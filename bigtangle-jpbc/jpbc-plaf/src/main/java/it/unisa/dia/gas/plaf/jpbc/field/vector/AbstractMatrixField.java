package it.unisa.dia.gas.plaf.jpbc.field.vector;

import it.unisa.dia.gas.jpbc.Field;
import it.unisa.dia.gas.plaf.jpbc.field.base.AbstractFieldOver;

import java.security.SecureRandom;

/**
 * @author Angelo De Caro (jpbclib@gmail.com)
 */
public abstract class AbstractMatrixField<F extends Field, E extends AbstractMatrixElement> extends AbstractFieldOver<F, E> {

    protected final int n, m;

    protected AbstractMatrixField(SecureRandom random, F targetField, int n, int m) {
        super(random, targetField);

        this.n = n;
        this.m = m;
    }

    public int getN() {
        return n;
    }

    public int getM() {
        return m;
    }

}
