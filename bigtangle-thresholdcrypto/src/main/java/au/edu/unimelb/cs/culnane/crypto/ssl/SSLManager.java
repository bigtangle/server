/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.ssl;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Wrapper to hold the TrustManagerFactory and KeyManagerFactory required for
 * SSL Sockets
 * 
 * @author Chris Culnane
 *
 */
public class SSLManager {

	/**
	 * Reference to TrustManagerFactory
	 */
	private TrustManagerFactory tmf;

	/**
	 * Reference to KeyManagerFactory
	 */
	private KeyManagerFactory kmf;

	/**
	 * Construct a new SSLManager with the specified TrustManagerFactory and
	 * KeyManagerFactory
	 * 
	 * @param tmf
	 *            TrustManagerFactory to use
	 * @param kmf
	 *            KeyManagerFactory to use
	 */
	public SSLManager(TrustManagerFactory tmf, KeyManagerFactory kmf) {
		this.tmf = tmf;
		this.kmf = kmf;
	}

	/**
	 * Gets the TrustManagerFactory
	 * 
	 * @return TrustManagerFactory
	 */
	public TrustManagerFactory getTrustManagerFactory() {
		return this.tmf;
	}

	/**
	 * Gets the KeyManagerFactory
	 * 
	 * @return KeyManagerFactory
	 */
	public KeyManagerFactory getKeyManagerFactory() {
		return this.kmf;
	}

}
