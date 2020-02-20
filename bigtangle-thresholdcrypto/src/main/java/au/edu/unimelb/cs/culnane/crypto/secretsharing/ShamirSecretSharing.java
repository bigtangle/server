/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.secretsharing;

import java.math.BigInteger;
import java.security.SecureRandom;

import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.P_Length;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;

/**
 * Generic ShamirSecret Sharing class. Provides functionality to compute
 * coefficients, shares and interpolate the result.
 * 
 * @author Chris Culnane
 *
 */
public class ShamirSecretSharing {

	/**
	 * Integer of numbers of peers
	 */
	private int peers;
	/**
	 * Integer of the threshold
	 */
	private int threshold;
	/**
	 * BigInteger array of alpha values
	 */
	private BigInteger[] alphas;

	/**
	 * BigInteger array of pre-computed sharing matrix
	 */
	private BigInteger[][] sharingMatrix;

	/**
	 * SecureRandom used to generate random polynomial
	 */
	private SecureRandom random;

	/**
	 * The underlying order of the sharing
	 */
	private BigInteger order;

	/**
	 * Create a new ShamirSecretSharing with the specified number of peers and
	 * threshold. The order of the sharing will be of order q of the supplied
	 * group.
	 * 
	 * @param peers
	 *            integer number of peers
	 * @param threshold
	 *            integer threshold
	 * @param group
	 *            Group parameters - sharing will be order q
	 * @param random
	 *            SecureRandom used for polynomial generation
	 */
	public ShamirSecretSharing(int peers, int threshold, Group group, SecureRandom random) {
		this(peers, threshold, group.get_q(), random);

	}

	/**
	 * Creates a new ShamirSecretSharing with the specified number of peers and
	 * threshold, and the specified order.
	 * 
	 * @param peers
	 *            integer number of peers
	 * @param threshold
	 *            integer threshold
	 * @param order
	 *            BigInteger order of sharing
	 * @param random
	 *            SecureRandom used for polynomial generation
	 */
	public ShamirSecretSharing(int peers, int threshold, BigInteger order, SecureRandom random) {
		this.peers = peers;
		this.threshold = threshold;
		this.order = order;
		this.random = random;
		calculateAlphas();
		computeSharingMatrix();
	}

	/**
	 * Pre-computes the alphas values, these are publicly know and are just the
	 * index from 1 to n where n is the number of peers.
	 */
	private void calculateAlphas() {
		alphas = new BigInteger[peers];
		for (int i = 0; i < peers; i++) {
			alphas[i] = BigInteger.valueOf(i + 1);
		}
	}

	/**
	 * Pre-computes the sharing matrix, which is just an i x j array where i,j =
	 * n (therefore n x n array). Each value is i^j mod order.
	 */
	protected void computeSharingMatrix() {
		sharingMatrix = new BigInteger[peers][peers];
		for (int i = 0; i < peers; i++) {
			for (int j = 0; j < peers; j++) {
				sharingMatrix[i][j] = alphas[i].modPow(BigInteger.valueOf(j), order);
			}
		}
	}

	/**
	 * Generates the coefficients for polynomial. Note coefficient[0] is equal
	 * to the secret. The other coefficients are generated at random. The only
	 * reason the secret is specified is to set coefficient[0], it is not used
	 * anywhere else in this method.
	 * 
	 * @param secret
	 *            BigInteger with secret to be shared
	 * @return Array of BigIntegers containing coefficients
	 */
	public BigInteger[] generateCoefficients(BigInteger secret) {
		BigInteger[] coefficients = new BigInteger[threshold];
		coefficients[0] = secret;
		for (int degree = 1; degree < threshold; degree++) {
			// TODO this could be optimised
			BigInteger randVal;
			while (true) {
				randVal = new BigInteger(order.bitLength(), random).mod(order);
				if (randVal.compareTo(order.subtract(BigInteger.ONE)) < 0) {
					break;
				}
			}
			coefficients[degree] = randVal;
		}
		return coefficients;
	}

	/**
	 * Generate secret shares of the specified coefficients, which should be
	 * generated via generateCoefficients(secret).
	 * 
	 * @param coefficients
	 *            BigInteger array of coefficients
	 * @return Array of SecretShares
	 */
	public SecretShare[] generateShares(BigInteger[] coefficients) {
		SecretShare[] shares = new SecretShare[peers];
		for (int a = 0; a < shares.length; a++) {
			shares[a] = new SecretShare(a + 1, BigInteger.ZERO);
		}
		for (int degree = 0; degree < threshold; degree++) {
			for (int peerCount = 0; peerCount < alphas.length; peerCount++) {
				shares[peerCount].add(sharingMatrix[peerCount][degree].multiply(coefficients[degree]).mod(order),
						order);
			}
		}
		return shares;
	}

	/**
	 * Computes the lagrange weights for this ShamirSecretSharing, based on the
	 * available peers.
	 * 
	 * @param availablePrivacyPeers
	 *            boolean array true if the share is available, false if not
	 * @return BigInteger array of Lagrange weights
	 */
	public BigInteger[] computeLagrangeWeights(boolean[] availablePrivacyPeers) {
		BigInteger[] lagrangeWeights = new BigInteger[peers];

		for (int privacyPeer = 0; privacyPeer < peers; privacyPeer++) {
			if (availablePrivacyPeers[privacyPeer]) {
				BigInteger weight = BigInteger.ONE;
				BigInteger nominator = BigInteger.ONE;
				BigInteger denominator = BigInteger.ONE;
				BigInteger alphaP = alphas[privacyPeer];

				for (int ppIndex = 0; ppIndex < alphas.length; ppIndex++) {
					if (ppIndex != privacyPeer && availablePrivacyPeers[ppIndex]) {
						nominator = nominator.multiply(alphas[ppIndex]).mod(order);
						denominator = denominator.multiply(alphas[ppIndex].subtract(alphaP).mod(order)).mod(order);
					}
				}

				weight = nominator.multiply(denominator.modInverse(order)).mod(order);
				lagrangeWeights[privacyPeer] = weight;
			} else {
				lagrangeWeights[privacyPeer] = BigInteger.ZERO;
			}
		}
		return lagrangeWeights;
	}

	/**
	 * Interpolates the shares to recover the secret. This will compute the
	 * Lagrange weights and then perform the interpolation.
	 * 
	 * @param shares
	 *            BigInteger array of available shares in peer order, if a share
	 *            is not available the array should contain null
	 * @return BigInteger of recovered secret
	 */
	public BigInteger interpolate(BigInteger[] shares) {
		BigInteger interpolationResult = BigInteger.ZERO;
		boolean[] availableShares = new boolean[peers];
		for (int i = 0; i < peers; i++) {
			if (shares[i] != null) {
				availableShares[i] = true;
			}
		}

		BigInteger[] lagrangeWeights = computeLagrangeWeights(availableShares);

		// Now interpolate
		for (int privacyPeer = 0; privacyPeer < shares.length; privacyPeer++) {
			if (availableShares[privacyPeer]) {
				interpolationResult = interpolationResult
						.add(lagrangeWeights[privacyPeer].multiply(shares[privacyPeer]).mod(order)).mod(order);
			}
		}
		return interpolationResult;
	}

	public static void main(String[] args) throws GroupException {
		ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526();
		group.initialise(P_Length.P3072);
		ShamirSecretSharing sharing = new ShamirSecretSharing(3, 3, group, new SecureRandom());

		// BigInteger one = new
		// BigInteger("a767aaf63c132cdeac88473534dff19eb0baa42418d9148626700e3b8d00de336da82d1e36bfeaf5a22b308cf8f97caa2ffcb3a5fff069411d71ed5d57931487125b4f1809d46fbc61137785a7e68886c94ca3506f3a8a9f7b66db7d78753dd1908bf0690b346e784c9332734a2fd3d0105d2ed6164abb750e8308c135365c066a9a5cbf2ad6f69fe1f1032c4629ca00856a87d5ef581d768ce9b6e097890cda9713963153c9eb24bfd7d995ae0086f69b1c642c0d0921a3cc1901140c414e52ea31fdcfab2c21df635563cd24e513214a334cacac0b93a6f67d712bf9aa1c3a98725dda5654320abfa27ed98a14731182ab6cb9a29bba00ce87ffec28b770bcf44b7fe0abbe5959c193e266cb11b511dca38e4fa6e9dbe36e60dceb4108f7f1b2468f866d09b7c6e450aed7a7d886e0925ca31051af3e0bf86adad887e64534250354d8a3147c1b7d166ddc9d6b06456c2c7de9ea287b7dbce063008ae2ed3712dd22c02b8932cc391a1da1a0befdf24535478605d73ab4fc2dfdafef8d86a8",16);
		// BigInteger two = new
		// BigInteger("4ecf55ec782659bd9000b3c8485721089caee5bcb0d60c3b23ddce6e8f99eff2d9449b96326c3ac8f30c58a063bef47670644d9832a68f670ab8d04cbcc714d6d4d568c2a6571d32dda13994ed6e92469e4d03b7383d27d3eace5a43fce3c3b532df74d6bbdf3d4aea8740d5181487b9d791f75a3fb11bac5b0594c9c908f9072b3ed3ced7499bbb1278956b91a7ed6a0a2418a493521ecf92fdc59ceb403c06adf18214e116393e52eca70a1cfde8a9b132dbeb3252de0d0cffbda764a71ae86708e0259a5835adc9da6976b148abfbe5b8f9a0b088bb61698df307d8f435db8728243feeae3384a3d6ca37e2ea997db284ef3ac1c9d8d38e31e48fb30340e381f45b3366050d363922416cb952ca18a4ee5239a6f27eecb66d8192903f413ed76a9547062c35e4fe07087e31ba1bb7201733e9b6604485d21272ad72cbdcce69f17f9783bea5bbbdcf1a14b53067082e0c9c3e49e46a18749017405fd6186b146c8597d733586828002828f9801857942f6619a487af5422a791160918b0d2",16);
		// BigInteger three = new
		// BigInteger("f63700e2b439869c3c88fafd7d3712a74d6989e0c9af20c14a4ddcaa1c9ace2646ecc8b4692c25be9537892d5cb87120a061013e3296f8a8282abdaa145a295de730b7dab02b8cef3eb4b11a95551acd6799a707a777b273663535c175590186c36b653fc713abc3371a734862445b89e7ef263055fbd72169889d8afe3f550d84bd9314a0121470ac166753da4ae0331243069713efcdbeb574c7af5f7cbdee63a496ffdef91dc54d0da9ccd6b7e261b8bdbfb321b4bbf28076d880eb43b5b9c77e39a7a192cf7fcb86f2c329b3e76637040485245235e5baca40da4d966694af733422719d9fe39ddd3baed4bac4f9f7d100166ba2bbd3fb0ee040419f8b3db7f25831ff687b779dac2577006fce29f823877b04012e73b21135bf86576f53a88449947a57e7da3649f70505c1122b7cb596e935e4396b9cea0489373b7ccd8755acc9a33139c050a6f164e470e7d7abcdd1ea2401b61fa3495440efa28a811ede380ff7c329355ac18ead333e434b2eac55cdec72f6be4921247c22a3dafb",16);
		// System.out.println("SecretKey" + sharing.interpolate(new
		// BigInteger[]{one,two,three}).toString(16));

		BigInteger secret = BigInteger.valueOf(123456789);
		BigInteger[] coefs = sharing.generateCoefficients(secret);
		SecretShare[] shares = sharing.generateShares(coefs);

		BigInteger[] gToCoefs = new BigInteger[coefs.length];
		BigInteger sum = BigInteger.ONE;
		for (int i = 0; i < gToCoefs.length; i++) {
			gToCoefs[i] = group.get_g().modPow(coefs[i], group.get_q());
			if (i > 0 && i < gToCoefs.length - 1) {
				sum = sum.multiply(gToCoefs[i]).mod(group.get_q());
			}
		}
		System.out.println("sum:" + sum);
		// BigInteger[] lagrangeWeights = sharing.computeLagrangeWeights(new
		// boolean[]{true,false,true});
		BigInteger[] available = new BigInteger[shares.length];

		available[0] = shares[0].getShare();
		available[1] = shares[1].getShare();
		available[2] = shares[2].getShare();
		// available[2]=shares[2].getShare();
		System.out.println(sharing.interpolate(available));

	}
}
