package net.bigtangle.equihash;

import java.nio.ByteBuffer;

import net.bigtangle.core.Sha256Hash;

public class EquihashSolver {
	public final static int N = 100;
	public final static int K = 5;
	
	public static EquihashProof calculateProof(Sha256Hash seed) {
		int[] seedInts = convertSeed(seed);
		EquihashProof proof = findProof(N, K, seedInts);	
		/*
		System.out.println("");
		System.out.print("inputs: ");
		for(int input : proof.getInputs())
			System.out.print(Integer.toHexString(input) + " ");*/
		return proof;
	}
	
	public static boolean testProof(Sha256Hash seed, EquihashProof proof) {
		int[] seedInts = convertSeed(seed);
		return validate(N, K, seedInts, proof.getNonce(), proof.getInputs());
	}
	
	private static int[] convertSeed(Sha256Hash seed) {
		byte[] bytes = seed.getBytes();
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		int[] result = new int[8];
		
		
		//System.out.print("java seed: ");
		for(int i = 0; i < 8; i++) {
			result[i] = buffer.getInt();
			//System.out.print(result[i] + " ");
		}
		//System.out.println("");*/
		return result;
	}
	
	private native static EquihashProof findProof(int n, int k, int[] seed);
	private native static boolean validate(int n, int k, int[] seed, int nonce, int[] inputs);
	
	static {
		Runtime.getRuntime().loadLibrary("equihash");
		//System.out.println("loaded equihash library");
	}
}
