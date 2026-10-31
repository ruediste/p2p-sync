package com.github.ruediste.p2psync.node;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class CredentialProtection implements AutoCloseable {
    private static final int PASSWORD_ITERATIONS = 210_000;

    record EncryptedCredential(byte[] ciphertext, byte[] salt, byte[] iv) {
    }

    private final char[] password;

    CredentialProtection(char[] password) {
        this.password = password.clone();
    }

    EncryptedCredential encrypt(byte[] plaintext) {
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        var random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);
        return new EncryptedCredential(crypt(Cipher.ENCRYPT_MODE, plaintext, salt, iv), salt, iv);
    }

    byte[] decrypt(EncryptedCredential credential) {
        return crypt(Cipher.DECRYPT_MODE, credential.ciphertext(), credential.salt(), credential.iv());
    }

    private byte[] crypt(int mode, byte[] input, byte[] salt, byte[] iv) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PASSWORD_ITERATIONS, 256);
        byte[] keyBytes = null;
        try {
            keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not unlock persisted user credentials", e);
        } finally {
            spec.clearPassword();
            if (keyBytes != null)
                Arrays.fill(keyBytes, (byte) 0);
        }
    }

    @Override
    public void close() {
        Arrays.fill(password, '\0');
    }
}