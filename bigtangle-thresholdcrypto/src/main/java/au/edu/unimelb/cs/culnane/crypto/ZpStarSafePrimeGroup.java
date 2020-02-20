/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

public class ZpStarSafePrimeGroup implements Group {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(ZpStarSafePrimeGroup.class);
	protected BigInteger p;
	protected BigInteger q;
	protected BigInteger g;
	protected static final String P_KEY = "p";
	protected static final String Q_KEY = "q";
	protected static final String G_KEY = "g";

	public ZpStarSafePrimeGroup() {
	}

	public ZpStarSafePrimeGroup(BigInteger p, BigInteger q, BigInteger g) {
		this.p = p;
		this.q = q;
		this.g = g;
	}

	public ZpStarSafePrimeGroup(StorageObject<?> store) throws StorageException {
		p = store.getBigInteger(P_KEY);
		q = store.getBigInteger(Q_KEY);
		g = store.getBigInteger(G_KEY);
	}

	@Override
	public void saveToFile(String filePath) throws IOException {
		JSONObject group = new JSONObject();
		group.put("p", this.p.toString(16));
		group.put("q", this.q.toString(16));
		group.put("g", this.g.toString(16));
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(filePath));
			bw.write(group.toString(2));
		} finally {
			if (bw != null) {
				bw.close();
			}
		}
	}

	@Override
	public void load(String filePath) throws IOException {
		BufferedReader br = null;
		StringBuffer sb = new StringBuffer();
		String line;
		try {
			br = new BufferedReader(new FileReader(filePath));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
			JSONObject group = new JSONObject(sb.toString());
			this.p = new BigInteger(group.getString("p"), 16);
			this.q = new BigInteger(group.getString("q"), 16);
			this.g = new BigInteger(group.getString("g"), 16);

		} finally {
			if (br != null) {
				br.close();
			}
		}

	}

	@Override
	public BigInteger getRandomValueInQ(SecureRandom rand) {
		while (true) {
			BigInteger randVal = new BigInteger(q.bitLength(), rand);
			if (randVal.compareTo(q.subtract(BigInteger.ONE)) <= 0) {
				return randVal;
			}
		}
	}

	public void initialise(int p_length, int certainty, SecureRandom rand) {
		logger.info("Initialising for p of lenght {}, cetainty {}", p_length, certainty);
		do {
			logger.info("Trying value of q");
			q = new BigInteger(p_length - 1, certainty, rand);
			// p = 2q+1
			p = q.shiftLeft(1).setBit(0);
			// TODO See "The number field sieve for integers of low weight",
			// Oliver Schirokauer.
		} while (!p.isProbablePrime(certainty));
		logger.info("p and q have been generated");
		g = createGenerator(rand);
		logger.info("Constructed generator g");
	}

	@Override
	public BigInteger createGenerator(SecureRandom rand) {
		BigInteger p_minus_one = p.subtract(BigInteger.ONE);
		// Construct a generator
		// p is of the form 2q+1, so j=2
		BigInteger j = BigInteger.valueOf(2);
		BigInteger h;
		BigInteger gen = null;
		do {
			h = new BigInteger(p_minus_one.bitLength(), rand);
			if (h.compareTo(BigInteger.ONE) < 1 || h.compareTo(p_minus_one) > -1) {
				continue;
			}
			gen = h.modPow(j, p);
		} while (gen == null || gen.equals(BigInteger.ONE));

		return gen;
	}

	@Override
	public BigInteger get_q() {
		return this.q;
	}

	@Override
	public BigInteger get_p() {
		return this.p;

	}

	@Override
	public BigInteger get_g() {
		return this.g;
	}

	@Override
	public boolean verify_q_divides_p_minus_one() {
		return (p.subtract(BigInteger.ONE).divide(q).intValue() == 2);
	}

	@Override
	public boolean verify_p_isPrime(int certainty) {
		return p.isProbablePrime(certainty);
	}

	@Override
	public boolean verify_q_isPrime(int certainty) {
		return q.isProbablePrime(certainty);
	}

	@Override
	public boolean verify_g_order() {
		return (g.modPow(q, p).equals(BigInteger.ONE));
	}

	@Override
	public boolean verify_element_order(BigInteger element) {
		return (element.modPow(q, p).equals(BigInteger.ONE));
	}

	@Override
	public String get_qString() {
		return this.q.toString(16);
	}

	@Override
	public String get_pString() {
		return this.p.toString(16);

	}

	@Override
	public String get_gString() {
		return this.g.toString(16);
	}

	@Override
	public boolean verify_element_order_q(BigInteger element) {
		return q.equals(q.divide(q.gcd(element)));
	}

	public void loadFromStorageObject(StorageObject<?> store) throws StorageException {
		p = store.getBigInteger(P_KEY);
		q = store.getBigInteger(Q_KEY);
		g = store.getBigInteger(G_KEY);
	}

	@Override
	public StorageObject<?> storeInStorageObject(StorageObject<?> store) {
		store.set(P_KEY, this.p);
		store.set(Q_KEY, this.q);
		store.set(G_KEY, this.g);
		return store;

	}

}
