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

import java.io.IOException;

/**
 * Abstract CommunicationLayerChannel specifying core abstract methods that must
 * be implemented. The Channel should handle opening, closing and sending of
 * messages.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            CommunicationLayerMessage type that the channel will use
 */
public abstract class CommunicationLayerChannel<E extends CommunicationLayerMessage<?>> implements Runnable {

	/**
	 * Gets the id of the channel
	 * 
	 * @return String id
	 */
	public abstract String getId();

	/**
	 * Closes the underlying channel
	 * 
	 * @throws IOException
	 */
	public abstract void close() throws IOException;

	/**
	 * Sends the byte array message along the channel
	 * 
	 * @param msg
	 *            byte array of message to send
	 * @throws IOException
	 */
	public abstract void send(byte[] msg) throws IOException;

	/**
	 * Sends the Message along the channel
	 * 
	 * @param msg
	 *            Message to send
	 * @throws IOException
	 */
	public abstract void send(E msg) throws IOException;

	/**
	 * Opens the channels and performs any necessary initialisation
	 * 
	 * @throws IOException
	 */
	public abstract void open() throws IOException;
}
