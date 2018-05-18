package net.bigtangle.equihash;

public class EquihashProof {
	private int seed;
	private int[] inputs;
	private int nonce;
	
	public EquihashProof(int seed, int nonce, int[] inputs) {
		this.seed = seed;
		this.nonce = nonce;
		this.inputs = inputs;
	}
	
	public boolean isValid() {
		return true;
	}

	public int getSeed() {
		return seed;
	}

	public int[] getInputs() {
		return inputs;
	}

	public int getNonce() {
		return nonce;
	}
	
	
}
