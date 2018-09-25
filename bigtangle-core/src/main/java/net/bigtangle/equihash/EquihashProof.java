/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.equihash;

import net.bigtangle.core.Utils;

public class EquihashProof {
    public final static int INPUT_COUNT = 2<<3;
    public final static int BYTE_LENGTH = INPUT_COUNT * 4 + 4;
    
	private int[] inputs;
	private int nonce;
	
	public EquihashProof(int nonce, int[] inputs) {
		this.nonce = nonce;
		this.inputs = inputs;
	}
	
	public boolean isValid() {
		return true;
	}

	public int[] getInputs() {
		return inputs;
	}

	public int getNonce() {
		return nonce;
	}
	
	// TODO Optimize serialization see e. g. ZCash
    public byte[] serialize() {
       int cursor = 0;
       byte[] outArray = new byte[BYTE_LENGTH];
       
       Utils.uint32ToByteArrayLE(nonce, outArray, cursor);
       cursor += 4;

       for (int i = 0; i < INPUT_COUNT; ++i) {
           Utils.uint32ToByteArrayLE(inputs[i], outArray, cursor);
           cursor += 4;
       }

	   return outArray;
	}
	
	public static EquihashProof from(byte[] data) {
	    int cursor = 0;
	    
        int nonce = (int) Utils.readUint32(data, cursor);
        cursor += 4;
        
        int[] inputArray = new int[INPUT_COUNT];
        for (int i = 0; i < INPUT_COUNT; ++i) {
            inputArray[i] = (int) Utils.readUint32(data, cursor); // No problem since the numbers are always small enough.
            cursor += 4;
        }
        
        return new EquihashProof(nonce, inputArray);
	}

    public static EquihashProof getDummy() {
        int nonce = 0;
        int[] inputArray = new int[INPUT_COUNT];
        return new EquihashProof(nonce, inputArray);
    }
}
