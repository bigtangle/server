/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.bls;

import au.edu.unimelb.cs.culnane.storage.Storable;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;
import au.edu.unimelb.cs.culnane.utils.IOUtils;
import au.edu.unimelb.cs.culnane.utils.IOUtils.EncodingType;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;

/**
 * BLSPrivateKey object that holds a JPBC Element of the private key. Includes
 * methods for loading and saving key to a file. Based on Coasca BLSPrivateKey
 * 
 * @author Chris Culnane
 *
 */
public class BLSPrivateKey implements Storable {
	/**
	 * JPBC Element that contains the actual private key
	 */
	private Element privateKey = null;

	/**
	 * String constant holding the field name of the private key in a JSON
	 * object
	 */
	private static final String PRIVATE_KEY = "privatekey";

	/**
	 * Constructs a new BLSPrivateKey from a StorageObject containing a private
	 * key representation
	 * 
	 * @param privKeyObj
	 *            StorageObject containing private key
	 * @param pairing
	 *            Pairing the private key belongs to
	 * @throws StorageException
	 */
	public BLSPrivateKey(StorageObject<?> privKeyObj, Pairing pairing) throws StorageException {
		// Create a new element
		privateKey = pairing.getZr().newElement();

		// Sets the element from the decoded bytes obtained from the
		// StorageObject
		privateKey.setFromBytes(IOUtils.decodeData(EncodingType.BASE64, privKeyObj.getString(PRIVATE_KEY)));
	}

	/**
	 * Constructs a BLSPrivateKey directly from a JPBC Element
	 * 
	 * @param privateKey
	 *            Element containing the private key
	 */
	public BLSPrivateKey(Element privateKey) {
		this.privateKey = privateKey;
	}

	/**
	 * Constructs a suitable formatted StorageObject containing the private key
	 * encoded as a Base64 String stored in a field with the name specified by
	 * PRIVATE_KEY
	 */
	@Override
	public StorageObject<?> storeInStorageObject(StorageObject<?> store) {
		store.set(PRIVATE_KEY, IOUtils.encodeData(EncodingType.BASE64, this.privateKey.toBytes()));
		return store;
	}

	/**
	 * Gets the underlying Element containing the private key
	 * 
	 * @return Element containing the private key
	 */
	public Element getKey() {
		return this.privateKey;
	}

}
