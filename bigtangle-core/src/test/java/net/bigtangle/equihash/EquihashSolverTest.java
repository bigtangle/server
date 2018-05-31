package net.bigtangle.equihash;

import static org.junit.Assert.assertEquals;

import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Block;
import net.bigtangle.core.Sha256Hash;

public class EquihashSolverTest {
	private Sha256Hash hash1 = Sha256Hash.of("test".getBytes());
	private Sha256Hash hash2 = Sha256Hash.of("test123".getBytes());
	
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
		
		assertEquals(true, EquihashSolver.testProof(hash1, proof1));
		assertEquals(true, EquihashSolver.testProof(hash2, proof2));
        assertEquals(false, EquihashSolver.testProof(hash2, proof1));
        assertEquals(false, EquihashSolver.testProof(hash1, proof2));
	}
	
	@Test 
	public void PerformanceTest() throws InterruptedException {
        Stopwatch watch = Stopwatch.createStarted();
        EquihashProof proof1 = EquihashSolver.calculateProof(hash1);
        long findTime = watch.elapsed(TimeUnit.MILLISECONDS);
        
        watch = Stopwatch.createStarted();
        EquihashSolver.testProof(hash1, proof1);
        long proofTime = watch.elapsed(TimeUnit.MICROSECONDS);

        System.out.println("\n*****");  
        System.out.println("Find proof time " + findTime + " ms");	    
        System.out.println("Test proof time " + proofTime + " µs");
        System.out.println("*****");  
	}
}
