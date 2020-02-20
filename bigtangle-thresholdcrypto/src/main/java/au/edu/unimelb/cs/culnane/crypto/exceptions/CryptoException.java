/**
 * 
 */
package au.edu.unimelb.cs.culnane.crypto.exceptions;

/**
 * Exception used when an error occurs within a Crypto operation
 * 
 * @author Chris Culnane
 *
 */
public class CryptoException extends Exception {

	/**
	 * Required for inherited serialisation.
	 */
	private static final long serialVersionUID = 7896501350209071471L;

	/**
	 * Constructs a new exception with {@code null} as its detail message.
	 */
	public CryptoException() {

	}

	/**
	 * Constructs a new exception with the specified detail message.
	 * 
	 * @param message
	 *            the detail message.
	 */
	public CryptoException(String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified cause and a detail message.
	 * 
	 * @param cause
	 *            the cause. A <tt>null</tt> value is permitted, and indicates
	 *            that the cause is nonexistent or unknown.
	 */
	public CryptoException(Throwable cause) {
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
	public CryptoException(String message, Throwable cause) {
		super(message, cause);
	}

}
