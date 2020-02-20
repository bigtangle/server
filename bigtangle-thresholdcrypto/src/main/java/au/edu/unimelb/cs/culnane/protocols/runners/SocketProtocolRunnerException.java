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
package au.edu.unimelb.cs.culnane.protocols.runners;

/**
 * @author chris
 *
 */
public class SocketProtocolRunnerException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1845043180299095640L;

	/**
	 * 
	 */
	public SocketProtocolRunnerException() {
	}

	/**
	 * @param message
	 */
	public SocketProtocolRunnerException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public SocketProtocolRunnerException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public SocketProtocolRunnerException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param message
	 * @param cause
	 * @param enableSuppression
	 * @param writableStackTrace
	 */
	public SocketProtocolRunnerException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
