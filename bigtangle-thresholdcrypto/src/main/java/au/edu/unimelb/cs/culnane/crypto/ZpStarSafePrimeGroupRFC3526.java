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

import java.math.BigInteger;

import org.json.JSONObject;

import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

public class ZpStarSafePrimeGroupRFC3526 extends ZpStarSafePrimeGroup implements Group {


	public ZpStarSafePrimeGroupRFC3526() {
	}

	public ZpStarSafePrimeGroupRFC3526(BigInteger p, BigInteger q, BigInteger g) {
		this.p = p;
		this.q = q;
		this.g = g;
	}

	public ZpStarSafePrimeGroupRFC3526(String pString, String qString, String gString) {
		this.p = new BigInteger(pString, 16);
		this.q = new BigInteger(qString, 16);
		this.g = new BigInteger(gString, 16);
	}

	public ZpStarSafePrimeGroupRFC3526(JSONObject groupObj) {
		this.p = new BigInteger(groupObj.getString(P_KEY), 16);
		this.q = new BigInteger(groupObj.getString(Q_KEY), 16);
		this.g = new BigInteger(groupObj.getString(G_KEY), 16);
	}

	public ZpStarSafePrimeGroupRFC3526(StorageObject<?> store) throws StorageException {
		p = store.getBigInteger(P_KEY);
		q = store.getBigInteger(Q_KEY);
		g = store.getBigInteger(G_KEY);
	}

	// P1536, P2046, P3072, P4096, P6144, P8192

	public void initialise(P_Length p_length) throws GroupException {
		switch (p_length) {
		case P1536:
			p = RFC3526.P1536;
			break;
		case P2046:
			p = RFC3526.P2046;
			break;
		case P3072:
			p = RFC3526.P3072;
			break;
		case P4096:
			p = RFC3526.P4096;
			break;
		case P6144:
			p = RFC3526.P6144;
			break;
		case P8192:
			p = RFC3526.P8192;
			break;
		default:
			throw new GroupException("p length not known");
		}
		q = p.subtract(BigInteger.ONE).divide(new BigInteger("2"));
		g = new BigInteger("2");
	}

}
