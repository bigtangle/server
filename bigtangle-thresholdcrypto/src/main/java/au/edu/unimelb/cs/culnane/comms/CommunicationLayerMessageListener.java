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
 * Interface for CommunicationlayerMessage listeners. Implement this to be able
 * to receive notifications of received messages
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            type of CommunicationLayerMessage
 */
public interface CommunicationLayerMessageListener<E extends CommunicationLayerMessage<?>> {

	/**
	 * Called when a message is received, with the received message passed as a
	 * parameter
	 * 
	 * @param msg
	 *            Received message
	 */
	public void messageReceived(E msg);
}
