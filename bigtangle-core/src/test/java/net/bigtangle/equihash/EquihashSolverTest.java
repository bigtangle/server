package net.bigtangle.equihash;

import static org.junit.Assert.assertEquals;

import java.security.MessageDigest;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.Sha256Hash;

public class EquihashSolverTest {
	private Sha256Hash hash1 = Sha256Hash.of("aaasdasda1".getBytes());
	private Sha256Hash hash2 = Sha256Hash.of("asd1".getBytes());
	
	@Test 
	public void RunProofSolver() {
		EquihashSolver.calculateProof(hash2);
	}	
	
	@Test
	public void RunProofSolverAndTestResultValidity() {
		EquihashProof proof = EquihashSolver.calculateProof(hash2);
		assertEquals(true, EquihashSolver.testProof(hash2, proof));
	}
	
	@Test
	public void CrossTest() {
		EquihashProof proof1 = EquihashSolver.calculateProof(hash1);
		EquihashProof proof2 = EquihashSolver.calculateProof(hash2);
		assertEquals(false, EquihashSolver.testProof(hash2, proof1));
		assertEquals(false, EquihashSolver.testProof(hash1, proof2));
	}
}
