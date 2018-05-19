package net.bigtangle.equihash;

public class EquihashSolver {
	public static EquihashProof calculateProof(int n, int k, int seed) {
		EquihashProof proof = runProofSolver(n, k, seed);	
		
		System.out.print("nonce from java " + proof.getNonce());
		
		return null;
	}
	
	private native static EquihashProof runProofSolver(int n, int k, int seed);
	
	static {
		Runtime.getRuntime().loadLibrary("equihash");
		System.out.println("loaded equihash library");
	}
	
	class SolverResult {
		public int[] inputs;
		public int nonce;
	}
}
