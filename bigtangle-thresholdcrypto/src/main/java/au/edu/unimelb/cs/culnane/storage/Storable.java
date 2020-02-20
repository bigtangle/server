/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.storage;

public interface Storable {

	/**
	 * Writes the contents of the object into the specified StorageObject.
	 * 
	 * @param store
	 *            StorageObject to write object to
	 * @returns returns reference to passed in store
	 */
	public StorageObject<?> storeInStorageObject(StorageObject<?> store);
}
