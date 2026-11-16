package cz.vsb.catering.business.domain;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2WithHmacSHA256 from the JDK with a random salt per password (A3).
 *  Stored in users.password_hash as "iterations:saltHex:hashHex". */
public final class PasswordHasher {
    private static final int ITERATIONS = 65_536;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return hash(password, salt, ITERATIONS);
    }

    /** Deterministic variant, used for the seed values and tests. */
    public static String hash(String password, byte[] salt, int iterations) {
        HexFormat hex = HexFormat.of();
        return iterations + ":" + hex.formatHex(salt) + ":" + hex.formatHex(derive(password, salt, iterations));
    }

    public static boolean matches(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.trim().split(":");
        if (parts.length != 3) return false;
        HexFormat hex = HexFormat.of();
        byte[] expected = hex.parseHex(parts[2]);
        byte[] actual = derive(password, hex.parseHex(parts[1]), Integer.parseInt(parts[0]));
        return MessageDigest.isEqual(expected, actual);           // constant-time comparison
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Prints a seed value: java PasswordHasher <password> <saltHex>. */
    public static void main(String[] args) {
        System.out.println(hash(args[0], HexFormat.of().parseHex(args[1]), ITERATIONS));
    }
}
