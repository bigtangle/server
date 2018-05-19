package net.bigtangle.equihash;

import static org.junit.Assert.assertEquals;

import java.security.MessageDigest;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.Sha256Hash;

public class EquihashSolverTest {
	
	private Sha256Hash hash = Sha256Hash.of("asd1".getBytes());
	
	@Test 
	public void RunProofSolver() {
		EquihashSolver.calculateProof(hash);
	}	
	
	@Test
	public void RunProofSolverAndTestResultValidity() {
		EquihashProof proof = EquihashSolver.calculateProof(hash);
		assertEquals(true, EquihashSolver.testProof(hash, proof));
	}
}
