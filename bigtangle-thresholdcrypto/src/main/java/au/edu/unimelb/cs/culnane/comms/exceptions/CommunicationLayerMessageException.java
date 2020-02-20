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
package au.edu.unimelb.cs.culnane.comms.exceptions;

import java.io.IOException;

/**
 * Exception used when an error occurs with a CommunicationLayerMessage
 * 
 * @author Chris Culnane
 *
 */
public class CommunicationLayerMessageException extends IOException {

	/**
	 * Required for inherited serialisation.
	 */
	private static final long serialVersionUID = 5445143882826886258L;

	/**
	 * Constructs a new exception with {@code null} as its detail message.
	 */
	public CommunicationLayerMessageException() {

	}

	/**
	 * Constructs a new exception with the specified detail message.
	 * 
	 * @param message
	 *            the detail message.
	 */
	public CommunicationLayerMessageException(String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified cause and a detail message.
	 * 
	 * @param cause
	 *            the cause. A <tt>null</tt> value is permitted, and indicates
	 *            that the cause is nonexistent or unknown.
	 */
	public CommunicationLayerMessageException(Throwable cause) {
		super(cause);
	}

	/**
	 * Constructs a new exception with the specified detail message and cause.
	 * <p>
	 * Note that the detail message associated with {@code cause} is <i>not</i>
	 * automatically incorporated in this exception's detail message.
	 * 
	 * @param message
	 *            the detail message.
	 * @param cause
	 *            the cause. A <tt>null</tt> value is permitted, and indicates
	 *            that the cause is nonexistent or unknown.
	 */
	public CommunicationLayerMessageException(String message, Throwable cause) {
		super(message, cause);
	}

}
