package net.bigtangle.equihash;

public class EquihashSolver {
	public static EquihashProof calculateProof() {
		EquihashProof proof = runProofSolver(100, 5, 33);	
		
		System.out.print(proof.getNonce());
		
		return null;
	}
	
	private native static EquihashProof runProofSolver(int n, int k, int seed);
	
	static {
		Runtime.getRuntime().loadLibrary("equihash");
	}
	
	class SolverResult {
		public int[] inputs;
		public int nonce;
	}
}
