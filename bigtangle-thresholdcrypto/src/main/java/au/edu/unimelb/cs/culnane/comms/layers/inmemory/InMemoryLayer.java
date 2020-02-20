/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.comms.layers.inmemory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayer;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessageConstructor;
import au.edu.unimelb.cs.culnane.comms.exceptions.CommunicationLayerMessageException;

/**
 * InMemoryLayer to allow communication within memory. Useful for development
 * when running multiple nodes on a single machine.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            CommunicationLayerMessage of appropriate type
 */
public class InMemoryLayer<E extends CommunicationLayerMessage<?>> extends CommunicationLayer<E> {

	/**
	 * Construct a new InMemoryLayer with the appropriate message constructor
	 * 
	 * @param msgConstructor
	 *            CommunicationLayerMessageConstructor of the appropriate type
	 */
	public InMemoryLayer(CommunicationLayerMessageConstructor<E> msgConstructor) {
		super(msgConstructor);
	}

	/**
	 * Receives a message in byte array form. Constructs a new message of the
	 * appropriate type and passes it on to the super class.
	 * 
	 * @param msg
	 *            byte array containing a message
	 * @throws CommunicationLayerMessageException
	 */
	public void receive(byte[] msg) throws CommunicationLayerMessageException {
		super.messageReceived(super.getMsgConstructor().constructMessage(msg));
		// use endpoint to response
	}

}
