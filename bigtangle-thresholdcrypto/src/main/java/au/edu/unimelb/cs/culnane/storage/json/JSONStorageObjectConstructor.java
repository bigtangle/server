/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
/**
 * 
 */
package au.edu.unimelb.cs.culnane.storage.json;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessageConstructor;

/**
 * ProtocolJSONConstructor to be used by the underlying communication layer to
 * construct ProtocolJSON objects from incoming messages
 * 
 * @author Chris Culnane
 *
 */
public class JSONStorageObjectConstructor extends CommunicationLayerMessageConstructor<JSONStorageObject> {

	@Override
	public JSONStorageObject constructMessage(byte[] msg) {
		return new JSONStorageObject(new String(msg));
	}

	@Override
	public JSONStorageObject createEmptyMessage() {
		return new JSONStorageObject();
	}

}
