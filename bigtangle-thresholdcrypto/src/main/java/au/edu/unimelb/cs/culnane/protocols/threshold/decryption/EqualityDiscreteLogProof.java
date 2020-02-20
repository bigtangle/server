/*  
 * Copyright (C) 2008-2009  TVS Group - University of  Surrey
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details. 
 * Contributors
 * 		S.Srinivasan - initial API and implementation
 * 		Chris Culnane - Modified to provide Storable interface and different constructors
 */
package au.edu.unimelb.cs.culnane.protocols.threshold.decryption;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.storage.Storable;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

public class EqualityDiscreteLogProof implements Storable {

	public static class STORAGE {
		public static final String A = "a";
		public static final String B = "b";
		public static final String C = "c";
		public static final String R = "r";

	}

	/**
	 * 
	 * Proof of Equality of Discrete Logarithm - aka Chaum Pederson
	 * <p>
	 * To prove that log v = log w, where v = g_1^x and w = g_2^x, let:
	 * <p>
	 * z = random in Z_q
	 * <p>
	 * a = g_1^z
	 * <p>
	 * b = g_2^z
	 * <p>
	 * c = hash(v,w,a,b)
	 * <p>
	 * r = (z + cx) mod q
	 * <p>
	 * The proof is (a,b,c,r).
	 * <p>
	 * To verify, check that g_1^r = av^c (mod p) and g_2^r = bw^c (mod p).
	 * <p>
	 * 
	 * @param <G>
	 *            the generic type corresponding to the underlying group
	 * 
	 * @author S.Srinivasan
	 */

	public BigInteger g1;
	public BigInteger g2;
	public BigInteger v;
	public BigInteger w;
	public BigInteger a;
	public BigInteger b;
	public BigInteger c;
	public BigInteger r;

	/**
	 * Constructs an EqualityDiscreteLogProof from specified parameters
	 * 
	 * @param g1
	 *            BigInteger of g1
	 * @param g2
	 *            BigInteger of g2
	 * @param v
	 *            BigInteger of v
	 * @param w
	 *            BigInteger of w
	 * @param a
	 *            BigInteger of a
	 * @param b
	 *            BigInteger of b
	 * @param c
	 *            BigInteger of c
	 * @param r
	 *            BigInteger of r
	 */
	public EqualityDiscreteLogProof(BigInteger g1, BigInteger g2, BigInteger v, BigInteger w, BigInteger a,
			BigInteger b, BigInteger c, BigInteger r) {
		this.g1 = g1;
		this.g2 = g2;
		this.v = v;
		this.w = w;
		this.a = a;
		this.b = b;
		this.c = c;
		this.r = r;
	}

	/**
	 * Constructs an EqualityDiscreteLogProof from specified StorageObject and
	 * parameters
	 * 
	 * @param proof
	 *            StorageObject containing proof
	 * @param g1
	 *            BigInteger of g1
	 * @param g2
	 *            BigInteger of g2
	 * @param v
	 *            BigInteger of v
	 * @param w
	 *            BigInteger of w
	 * @throws StorageException
	 *             - if StorageObject does not containing necessary fields
	 */
	public EqualityDiscreteLogProof(StorageObject<?> proof, BigInteger g1, BigInteger g2, BigInteger v, BigInteger w)
			throws StorageException {
		this.g1 = g1;
		this.g2 = g2;
		this.v = v;
		this.w = w;
		this.a = proof.getBigInteger(STORAGE.A);
		this.b = proof.getBigInteger(STORAGE.B);
		this.c = proof.getBigInteger(STORAGE.C);
		this.r = proof.getBigInteger(STORAGE.R);

	}

	/**
	 * Construct a proof of Equality of discrete logarithms
	 *
	 * @param <G>
	 *            the generic type corresponding to the underlying group
	 * 
	 * @param grp
	 *            the underlying group
	 * @param g1
	 * @param g2
	 * @param x
	 * @return the proof of Equality of discrete logarithms
	 * @throws NoSuchAlgorithmException
	 */
	public static EqualityDiscreteLogProof constructProof(Group group, BigInteger g1, BigInteger g2, BigInteger v,
			BigInteger w, BigInteger x) throws NoSuchAlgorithmException {

		BigInteger z = group.getRandomValueInQ(new SecureRandom());
		BigInteger a = g1.modPow(z, group.get_p());
		BigInteger b = g2.modPow(z, group.get_p());
		MessageDigest digest = MessageDigest.getInstance("SHA-256");

		digest.update(v.toByteArray());
		digest.update(w.toByteArray());
		digest.update(a.toByteArray());
		digest.update(b.toByteArray());

		BigInteger c = new BigInteger(1, digest.digest());

		BigInteger r = z.add(c.multiply(x)).mod(group.get_q());

		return new EqualityDiscreteLogProof(g1, g2, v, w, a, b, c, r);
	}

	/**
	 * public static EDLProof constructNewProof(Group group, BigInteger g1,
	 * BigInteger g2, BigInteger y1, BigInteger y2, BigInteger x) throws
	 * NoSuchAlgorithmException { // y1 is partial decryption // y2 is public
	 * verification data for share BigInteger r = group.getRandomValueInQ(new
	 * SecureRandom());
	 * 
	 * BigInteger a1 = g1.modPow(r, group.get_p()); BigInteger a2 = g2.modPow(r,
	 * group.get_p());
	 * 
	 * MessageDigest digest = MessageDigest.getInstance("SHA-256");
	 * 
	 * digest.update(group.get_p().toByteArray());
	 * digest.update(group.get_q().toByteArray());
	 * digest.update(g1.toByteArray()); digest.update(y1.toByteArray());
	 * digest.update(g2.toByteArray()); digest.update(y2.toByteArray());
	 * digest.update(a1.toByteArray()); digest.update(a2.toByteArray());
	 * 
	 * BigInteger c = new BigInteger(1, digest.digest());
	 * 
	 * BigInteger b =
	 * r.subtract(c.multiply(x).mod(group.get_p())).mod(group.get_p()); return
	 * new EDLProof(g1, g2, y1, y2, c, b); }
	 * 
	 * public static boolean verify(Group group, EDLProof E) throws
	 * NoSuchAlgorithmException { BigInteger gbyc = E.g1.modPow(E.b,
	 * group.get_p()).multiply(E.y1.modPow(E.c,
	 * group.get_p())).mod(group.get_p()); BigInteger ybyc = E.g2.modPow(E.b,
	 * group.get_p()).multiply(E.y2.modPow(E.c,
	 * group.get_p())).mod(group.get_p());
	 * 
	 * MessageDigest digest = MessageDigest.getInstance("SHA-256");
	 * 
	 * digest.update(group.get_p().toByteArray());
	 * digest.update(group.get_q().toByteArray());
	 * digest.update(E.g1.toByteArray()); digest.update(E.y1.toByteArray());
	 * digest.update(E.g2.toByteArray()); digest.update(E.y2.toByteArray());
	 * digest.update(gbyc.toByteArray()); digest.update(ybyc.toByteArray());
	 * 
	 * BigInteger c = new BigInteger(1, digest.digest()); System.out.println(c);
	 * return false; }
	 */

	/**
	 * Verify that a proof of Equality of discrete logarithms is valid.
	 *
	 * @param <G>
	 *            the generic type corresponding to the underlying group
	 * @param grp
	 *            the underlying group
	 * @param E
	 *            the proof
	 * @return true if proof is valid
	 */
	public static boolean verify(Group group, EqualityDiscreteLogProof E) {
		return E.g1.modPow(E.r, group.get_p()).equals(E.v.modPow(E.c, group.get_p()).multiply(E.a).mod(group.get_p()))
				&& E.g2.modPow(E.r, group.get_p())
						.equals(E.w.modPow(E.c, group.get_p()).multiply(E.b).mod(group.get_p()));

	}

	@Override
	public StorageObject<?> storeInStorageObject(StorageObject<?> store) {
		store.set(STORAGE.A, a);
		store.set(STORAGE.B, b);
		store.set(STORAGE.C, c);
		store.set(STORAGE.R, r);
		return store;
	}

}
