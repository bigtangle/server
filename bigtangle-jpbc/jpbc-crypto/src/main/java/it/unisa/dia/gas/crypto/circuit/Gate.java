package it.unisa.dia.gas.crypto.circuit;

/**
* @author Angelo De Caro (jpbclib@gmail.com)
*/
public interface Gate<V> {

    public static enum Type {INPUT, AND, OR, NAND}

    Type getType();

    int getIndex();

    int getDepth();

    int getInputIndexAt(int index);

    int getInputNum();

    Gate<V> getInputAt(int index);

    Gate<V> evaluate();

    V get();

    Gate<V> set(V value);

    Gate<V> putAt(int index, V value);

    V getAt(int index);

}
