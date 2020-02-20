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
 * Abstract utility class to provide a method to construct an appropriate
 * CommunicationLayerMessage from a byte array.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            underlying type of CommunicationLayerMessage
 */
public abstract class CommunicationLayerMessageConstructor<E extends CommunicationLayerMessage<?>> {

	/**
	 * Constructs a CommunicationLayerMessage of the appropriate type from the
	 * byte array
	 * 
	 * @param msg
	 *            byte array of the message
	 * @return CommunicationLayerMessage of appropriate type containing byte
	 *         array message
	 */
	public abstract E constructMessage(byte[] msg);

	/**
	 * Creates and returns an empty message of the appropriate type
	 * 
	 * @return Empty CommunicationLayerMessage
	 */
	public abstract E createEmptyMessage();

}
