package net.bigtangle.equihash;

import java.security.MessageDigest;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.Sha256Hash;

public class EquihashSolverTest {
	
	@Test 
	public void TestComputeProof() {
		EquihashSolver.calculateProof(Sha256Hash.of("asd1".getBytes()));
		Block b;
	}	
}
