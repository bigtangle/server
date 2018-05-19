package net.bigtangle.equihash;

public class EquihashProof {
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
	
	
}
