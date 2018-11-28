/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import org.junit.Test;

import net.bigtangle.core.Message;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.ProtocolException;
import net.bigtangle.core.VarInt;
import net.bigtangle.params.MainNetParams;

public class MessageTest {

    // If readStr() is vulnerable this causes OutOfMemory
    @Test(expected = ProtocolException.class)
    public void readStrOfExtremeLength() throws Exception {
        NetworkParameters params = MainNetParams.get();
        VarInt length = new VarInt(Integer.MAX_VALUE);
        byte[] payload = length.encode();
        new VarStrMessage(params, payload);
    }

    static class VarStrMessage extends Message {
        public VarStrMessage(NetworkParameters params, byte[] payload) {
            super(params, payload, 0);
        }

        @Override
        protected void parse() throws ProtocolException {
            readStr();
        }
    }

    // If readBytes() is vulnerable this causes OutOfMemory
    @Test(expected = ProtocolException.class)
    public void readByteArrayOfExtremeLength() throws Exception {
        NetworkParameters params = MainNetParams.get();
        VarInt length = new VarInt(Integer.MAX_VALUE);
        byte[] payload = length.encode();
        new VarBytesMessage(params, payload);
    }

    static class VarBytesMessage extends Message {
        public VarBytesMessage(NetworkParameters params, byte[] payload) {
            super(params, payload, 0);
        }

        @Override
        protected void parse() throws ProtocolException {
            readByteArray();
        }
    }
}
