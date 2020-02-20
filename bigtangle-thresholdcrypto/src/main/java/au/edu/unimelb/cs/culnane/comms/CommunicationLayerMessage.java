/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.comms;

/**
 * Interface for CommuncationLayerMessage
 * 
 * @author Chris Culnane
 *
 * @param <T>
 *            underlying message type
 */
public interface CommunicationLayerMessage<T> {

	/**
	 * Gets a raw byte array of the message contents
	 * 
	 * @return byte array of message contents
	 */
	public byte[] getRawMessage();

	/**
	 * Gets a String of the message type
	 * 
	 * @return String of message type
	 */
	public String getType();
	

}
