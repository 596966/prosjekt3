package no.hvl.dat110.util;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {

	public static BigInteger hashOf(String entity) {
		BigInteger hashint = null;

		try {
			// Get MD5 MessageDigest instance
			MessageDigest md = MessageDigest.getInstance("MD5");

			// Compute the hash of the input string
			byte[] digest = md.digest(entity.getBytes("UTF-8"));

			// Convert to hex string
			String hex = toHex(digest);

			// Convert hex string to BigInteger
			hashint = new BigInteger(hex, 16);

		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return hashint;
	}

	public static BigInteger addressSize() {
		// 2^bitSize()
		return BigInteger.valueOf(2).pow(bitSize());
	}

	public static int bitSize() {
		int digestlen = 0;

		try {
			// Get MD5 digest length in bytes
			digestlen = MessageDigest.getInstance("MD5").getDigestLength();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		// Return length in bits
		return digestlen * 8;
	}

	public static String toHex(byte[] digest) {
		StringBuilder strbuilder = new StringBuilder();
		for(byte b : digest) {
			strbuilder.append(String.format("%02x", b&0xff));
		}
		return strbuilder.toString();
	}
}