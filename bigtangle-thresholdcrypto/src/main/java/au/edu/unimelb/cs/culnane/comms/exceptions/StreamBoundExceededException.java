package au.edu.unimelb.cs.culnane.comms.exceptions;
/*******************************************************************************
 * Copyright (c) 2013, 2016 Coasca Limited.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Chris Culnane - initial API and implementation
 *     Matthew Casey - review
 ******************************************************************************/


/**
 * Exception used when the bounded stream is exceeded.
 * 
 * @author Chris Culnane
 */
public class StreamBoundExceededException extends Exception {

  /**
   * Required for inherited serialisation.
   */
  private static final long serialVersionUID = -8642437822029324024L;

  /**
   * Constructs a new exception with {@code null} as its detail message.
   */
  public StreamBoundExceededException() {
    super();
  }

  /**
   * Constructs a new exception with the specified detail message.
   * 
   * @param message
   *          the detail message.
   */
  public StreamBoundExceededException(String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified detail message and cause.
   * <p>
   * Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in this exception's detail
   * message.
   * 
   * @param message
   *          the detail message.
   * @param cause
   *          the cause. A <tt>null</tt> value is permitted, and indicates that the cause is nonexistent or unknown.
   */
  public StreamBoundExceededException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new exception with the specified detail message, cause, suppression enabled or disabled, and writable stack trace
   * enabled or disabled.
   * 
   * @param message
   *          the detail message.
   * @param cause
   *          the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
   * @param enableSuppression
   *          whether or not suppression is enabled or disabled
   * @param writableStackTrace
   *          whether or not the stack trace should be writable
   */
  public StreamBoundExceededException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  /**
   * Constructs a new exception with the specified cause and a detail message.
   * 
   * @param cause
   *          the cause. A <tt>null</tt> value is permitted, and indicates that the cause is nonexistent or unknown.
   */
  public StreamBoundExceededException(Throwable cause) {
    super(cause);
  }
}
