package net.bigtangle.equihash;

import java.nio.ByteBuffer;

import net.bigtangle.core.Sha256Hash;

public class EquihashSolver {    	
	public static EquihashProof calculateProof(int n, int k, Sha256Hash seed) {
		int[] seedInts = convertSeed(seed);
		EquihashProof proof = findProof(n, k, seedInts);	
		return proof;
	}
	
	public static boolean testProof(int n, int k, Sha256Hash seed, EquihashProof proof) {
		int[] seedInts = convertSeed(seed);
		return validate(n, k, seedInts, proof.getNonce(), proof.getInputs());
	}
	
	private static int[] convertSeed(Sha256Hash seed) {
		byte[] bytes = seed.getBytes();
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		int[] result = new int[8];
		
		for(int i = 0; i < 8; i++) {
			result[i] = buffer.getInt();
		}
		
		return result;
	}
	
	private native static EquihashProof findProof(int n, int k, int[] seed);
	private native static boolean validate(int n, int k, int[] seed, int nonce, int[] inputs);
	
	static {
		if(System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
			System.loadLibrary("equihash" + System.getProperty("sun.arch.data.model"));
		} else {
			System.loadLibrary("equihash" );
		}
	}
}
