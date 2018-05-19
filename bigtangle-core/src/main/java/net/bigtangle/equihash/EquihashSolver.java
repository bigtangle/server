package net.bigtangle.equihash;

import java.nio.ByteBuffer;

import net.bigtangle.core.Sha256Hash;

public class EquihashSolver {
	public final static int N = 100;
	public final static int K = 5;
	
	public static EquihashProof calculateProof(Sha256Hash seed) {
		
		EquihashProof proof = findProof(N, K, convertSeed(seed));	
		
		System.out.print("nonce from java " + proof.getNonce());
		
		return null;
	}
	
	private static int[] convertSeed(Sha256Hash seed) {
		byte[] bytes = seed.getBytes();
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		int[] result = new int[8];
		
		for(int i = 0; i < 8; i++) {
			result[i] = buffer.getInt();
			System.out.println(result[i]);
		}
		
		return result;
	}
	
	private native static EquihashProof findProof(int n, int k, int[] seed);
	private native static boolean validate(int n, int k, int[] seed, int nonce, int[] inputs);
	
	static {
		Runtime.getRuntime().loadLibrary("equihash");
		System.out.println("loaded equihash library");
	}
	
	class SolverResult {
		public int[] inputs;
		public int nonce;
	}
}
