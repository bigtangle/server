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

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessageConstructor;

/**
 * Constructs a JSONMessage from the an underlying byte array
 * 
 * @author Chris Culnane
 *
 */
public class JSONCommLayerMessageConstructor extends CommunicationLayerMessageConstructor<JSONCommLayerMessage> {

	@Override
	public JSONCommLayerMessage constructMessage(byte[] msg) {
		return new JSONCommLayerMessage(msg);
	}

	@Override
	public JSONCommLayerMessage createEmptyMessage() {

		return new JSONCommLayerMessage();
	}

}
