/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IO utility class that includes static methods for loading and saving
 * JSONObject to file, and encoding and decoding data.
 * 
 * @author Chris Culnane
 *
 */
public class IOUtils {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(IOUtils.class);

	/**
	 * Enumerator of accepted encoding types
	 * 
	 * @author Chris Culnane
	 *
	 */
	public enum EncodingType {
		BASE64, HEX
	}

	private IOUtils() {
	}

	/**
	 * Utility method for decoding Strings into byte arrays. Currently provides
	 * Hex and Base64.
	 * 
	 * @param encType
	 *            EncodingType to use (Base64, Hex)
	 * @param data
	 *            string of data to be decoded
	 * @return byte array of decoded data
	 */
	public static byte[] decodeData(EncodingType encType, String data) {
		try {
			switch (encType) {
			case BASE64:
				return Base64.decodeBase64(data);
			case HEX:
				return Hex.decodeHex(data.toCharArray());
			default:
				return data.getBytes();
			}
		} catch (DecoderException e) {
			logger.warn("Exception decoding Hex String", e);
			return null;
		}
	}

	/**
	 * Utility method for encoding byte arrays into strings. Currently provides
	 * Hex and Base64 encoding.
	 * 
	 * @param encType
	 *            EncodingType to use (Base64, Hex)
	 * @param data
	 *            byte array of data to be encoded
	 * @return String of the encoded data
	 */
	public static String encodeData(EncodingType encType, byte[] data) {
		switch (encType) {
		case BASE64:
			return Base64.encodeBase64String(data);
		case HEX:
			return Hex.encodeHexString(data);
		default:
			return new String(data);
		}
	}

	/**
	 * Reads a JSONObject from a file
	 * 
	 * @param file
	 *            String path to file
	 * @return JSONObject that was contained within the file
	 * @throws IOException
	 *             if there is an exception reading the file
	 */
	public static JSONObject readJSONObjectFromFile(String file) throws IOException {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(file));
			StringBuffer sb = new StringBuffer();
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
			return new JSONObject(sb.toString());
		} finally {
			if (br != null) {
				br.close();
			}
		}
	}

	/**
	 * Writes a JSONObject to a file
	 * 
	 * @param file
	 *            String path to file to write JSONObject to
	 * @param obj
	 *            JSONObject to be written to file
	 * @throws IOException
	 *             if an exception occurs writing to the file
	 */
	public static void writeToFile(String file, JSONObject obj) throws IOException {
		writeToFile(file, obj.toString(2));
	}

	/**
	 * Reads a JSONArray from a file
	 * 
	 * @param file
	 *            String path to file
	 * @return JSONArray that was contained within the file
	 * @throws IOException
	 *             if there is an exception reading the file
	 */
	public static JSONArray readJSONArrayFromFile(String file) throws IOException {
		return new JSONArray(readStringFromFile(file));

	}

	/**
	 * Reads the contents of a file into a string
	 * 
	 * @param file
	 *            String path to file to read
	 * @return String of file contents
	 * @throws IOException
	 *             if an error occurs during reading
	 */
	public static String readStringFromFile(String file) throws IOException {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(file));
			StringBuffer sb = new StringBuffer();
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
			return sb.toString();
		} finally {
			if (br != null) {
				br.close();
			}
		}
	}

	/**
	 * Writes a String to a file
	 * 
	 * @param file
	 *            String path to file to write String to
	 * @param data
	 *            String of data to be written
	 * @throws IOException
	 *             if an error occurs during writing
	 */
	public static void writeToFile(String file, String data) throws IOException {
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(file));
			bw.write(data);

		} finally {
			if (bw != null) {
				bw.close();
			}
		}
	}

	/**
	 * Writes a JSONArray to a file
	 * 
	 * @param file
	 *            String path to file to write JSONObject to
	 * @param obj
	 *            JSONObject to be written to file
	 * @throws IOException
	 *             if an exception occurs writing to the file
	 */
	public static void writeToFile(String file, JSONArray obj) throws IOException {
		writeToFile(file, obj.toString(2));
	}



}
