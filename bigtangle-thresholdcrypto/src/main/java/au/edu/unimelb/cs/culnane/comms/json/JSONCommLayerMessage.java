/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.comms.json;

import java.util.Locale;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;

/**
 * JSON based CommunicationLayerMessage. Extends JSONObject, only adding certain
 * additional functionality. Implements CommunicationLayerMessage to allow its
 * use on the CommunicationLayer
 * 
 * @author Chris Culnane
 *
 */
public class JSONCommLayerMessage extends JSONObject implements CommunicationLayerMessage<JSONCommLayerMessage> {

	/**
	 * String of the underlying type of the object
	 */
	private static final String TYPE = "JSONObject";

	/**
	 * Construct a JSONMessage from a String of JSON
	 * 
	 * @param msgString
	 *            String of JSON
	 */
	public JSONCommLayerMessage(String msgString) {
		super(msgString);
	}

	/**
	 * Constructs a JSONMessage from a byte array containing a string of JSON
	 * 
	 * @param bytes
	 *            byte arrey of String of JSON
	 */
	public JSONCommLayerMessage(byte[] bytes) {
		super(new String(bytes));
	}

	/**
	 * Gets a byte array representation of the JSON
	 * 
	 */
	public byte[] getRawMessage() {
		return super.toString().getBytes();
	}

	/**
	 * Gets the type of this message
	 */
	public String getType() {
		return TYPE;
	}

	/**
	 * Constructs an empty JSONMessage
	 */
	public JSONCommLayerMessage() {
		super();
	}

	/**
	 * Construct a JSONObject from a subset of another JSONObject. An array of
	 * strings is used to identify the keys that should be copied. Missing keys
	 * are ignored.
	 * 
	 * @param jo
	 *            A JSONObject
	 * @param names
	 *            An array of strings
	 */
	public JSONCommLayerMessage(JSONObject jo, String[] names) {
		super(jo, names);
	}

	/**
	 * Construct a JSONObject from a JSONTokener
	 * 
	 * @param x
	 *            A JSONTokener object containing the source string.
	 * @throws JSONException
	 *             If there is a syntax error in the source string or a
	 *             duplicated key.
	 */
	public JSONCommLayerMessage(JSONTokener x) throws JSONException {
		super(x);
	}

	/**
	 * Construct a JSONObject from a Map.
	 * 
	 * @param map
	 *            A map object that can be used to initialize the contents of
	 *            the JSONObject.
	 */
	public JSONCommLayerMessage(Map<?, ?> map) {
		super(map);
	}

	/**
	 * Construct a JSONObject from an Object, using reflection to find the
	 * public members. The resulting JSONObject's keys will be the strings from
	 * the names array, and the values will be the field values associated with
	 * those keys in the object. If a key is not found or not visible, then it
	 * will not be copied into the new JSONObject.
	 * 
	 * @param object
	 *            An object that has fields that should be used to make a
	 *            JSONObject.
	 * @param names
	 *            An array of strings, the names of the fields to be obtained
	 *            from the object.
	 */
	public JSONCommLayerMessage(Object object, String[] names) {
		super(object, names);
	}

	/**
	 * Construct a JSONObject from an Object using bean getters. It reflects on
	 * all of the public methods of the object. For each of the methods with no
	 * parameters and a name starting with "get" or "is" followed by an
	 * uppercase letter, the method is invoked, and a key and the value returned
	 * from the getter method are put into the new JSONObject. The key is formed
	 * by removing the "get" or "is" prefix. If the second remaining character
	 * is not upper case, then the first character is converted to lower case.
	 * For example, if an object has a method named "getName", and if the result
	 * of calling object.getName() is "Larry Fine", then the JSONObject will
	 * contain "name": "Larry Fine".
	 * 
	 * @param bean
	 *            An object that has getter methods that should be used to make
	 *            a JSONObject.
	 */
	public JSONCommLayerMessage(Object bean) {
		super(bean);
	}

	/**
	 * Construct a JSONObject from a ResourceBundle.
	 * 
	 * @param baseName
	 *            The ResourceBundle base name.
	 * @param locale
	 *            The Locale to load the ResourceBundle for.
	 * @throws JSONException
	 *             If any JSONExceptions are detected.
	 */
	public JSONCommLayerMessage(String baseName, Locale locale) throws JSONException {
		super(baseName, locale);
	}

	@Override
	public String toString() {
		return super.toString();
	}

}
