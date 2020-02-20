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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.StringTokenizer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.utils.IOUtils;
import au.edu.unimelb.cs.culnane.utils.IOUtils.EncodingType;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.jpbc.PairingParameters;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;

/**
 * Class that represents the system parameters for BLS. It includes the
 * generator and more importantly the underlying pairing. This class provides
 * methods for generating a new generator, saving the description to a file, and
 * loading the description from a file.
 * 
 * This object extends JSONObject to allow easy access to the pairing
 * description and to load and save the file.
 * 
 * @author Chris Culnane
 *
 */
public class BLSSystemParameters extends JSONObject implements PairingParameters {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(BLSSystemParameters.class);

	/**
	 * Static String that is used as the Key in the underlying JSONObject for
	 * the Generator
	 */
	public static final String GENERATOR = "G";

	/**
	 * JPBC Pairing used for BLS
	 */
	private Pairing pairing;

	/**
	 * Element containing the generator
	 */
	private Element g;

	public static void main(String[] args) throws IOException {
		BLSSystemParameters.generateNewParameters(args[0]).saveParameters(args[1]);
	}

	/**
	 * Gets the pairing
	 * 
	 * @return Pairing for this group
	 */
	public Pairing getPairing() {
		return this.pairing;
	}

	/**
	 * serialization id
	 */
	private static final long serialVersionUID = -8672819245488073719L;

	/**
	 * Create new BLSSystemParameters object from a JSONObject
	 * 
	 * @param paramsObj
	 *            JSONObject containing system parameters
	 */
	public BLSSystemParameters(JSONObject paramsObj) {
		super(paramsObj.toString());
		pairing = PairingFactory.getPairing(this);
		if (super.has(GENERATOR)) {
			logger.info("Loading generator from file");

			g = pairing.getG2().newElement();
			// Set the element value from the bytes decoded from the JSON
			g.setFromBytes(IOUtils.decodeData(EncodingType.BASE64, super.getString(GENERATOR)));
			g = g.getImmutable();
		} else {
			logger.info("Creating new random generator");
			g = pairing.getG2().newRandomElement().getImmutable();
			super.put(GENERATOR, IOUtils.encodeData(EncodingType.BASE64, g.toBytes()));
		}

	}

	/**
	 * Loads the BLSSystemParameters from a file that contains a JSON
	 * representation of the parameters
	 * 
	 * @param parameterPath
	 *            String file path
	 * @throws IOException
	 */
	public BLSSystemParameters(String parameterPath) throws IOException {
		this(IOUtils.readJSONObjectFromFile(parameterPath));
	}

	/**
	 * Saves the parameters as a JSON String in the specified file
	 * 
	 * @param parameterPath
	 *            String file to write parameters to
	 * @throws IOException
	 */
	public void saveParameters(String parameterPath) throws IOException {
		IOUtils.writeToFile(parameterPath, this);
	}

	/**
	 * Gets the generator g Element
	 * 
	 * @return Element g
	 */
	public Element getGenerator() {
		return this.g;

	}

	/**
	 * Generates new BLSSystemParameters from the base curve parameters provided
	 * by JPBC. This will create a new generator as well.
	 * 
	 * @param curvePath
	 *            String path to curve parameters
	 * @return BLSSystemParameters initialised with curve parameters and a new
	 *         generator
	 * @throws IOException
	 */
	public static BLSSystemParameters generateNewParameters(String curvePath) throws IOException {
		JSONObject loadParams = new JSONObject();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(curvePath));
			while (true) {
				String line = reader.readLine();
				if (line == null)
					break;
				line = line.trim();
				if (line.length() == 0)
					continue;
				if (line.startsWith("#"))
					continue;

				StringTokenizer tokenizer = new StringTokenizer(line, "= :", false);
				String key = tokenizer.nextToken();
				String value = tokenizer.nextToken();
				loadParams.put(key, value);
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		// Create the generator at random
		BLSSystemParameters newparams = new BLSSystemParameters(loadParams);

		return newparams;

	}

	@Override
	public boolean containsKey(String key) {
		return super.has(key);
	}

	@Override
	public BigInteger getBigInteger(String key, BigInteger defaultValue) {
		return super.optBigInteger(key, defaultValue);
	}

	@Override
	public BigInteger getBigIntegerAt(String key, int index) {
		return getBigInteger(key + index);
	}

	@Override
	public byte[] getBytes(String key) {
		String val = super.getString(key);
		return IOUtils.decodeData(EncodingType.BASE64, val);
	}

	@Override
	public byte[] getBytes(String key, byte[] defaultValue) {
		if (super.has(key)) {
			String val = super.getString(key);
			byte[] data = IOUtils.decodeData(EncodingType.BASE64, val);
			if (data == null) {
				return defaultValue;
			} else {
				return data;
			}
		} else {
			return defaultValue;
		}

	}

	@Override
	public int getInt(String key, int defaultValue) {
		return super.optInt(key, defaultValue);
	}

	@Override
	public long getLong(String key, long defaultValue) {
		return super.optLong(key, defaultValue);
	}

	@Override
	public Object getObject(String key) {
		return super.get(key);
	}

	@Override
	public String getString(String key, String defaultValue) {
		return super.optString(key, defaultValue);
	}

	@Override
	public String toString(String arg0) {
		return super.toString();
	}

}
