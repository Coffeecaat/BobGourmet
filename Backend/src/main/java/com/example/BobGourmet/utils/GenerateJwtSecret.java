package com.example.BobGourmet.utils;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class GenerateJwtSecret {
    public static void main(String[] args) throws NoSuchAlgorithmException {

        // getting KeyGenerator Instance for HMAC-SHA-256
        KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");

        // initializing to generate 256bit key
        keyGen.init(256);

        SecretKey secretKey = keyGen.generateKey();

        String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

        System.out.println("Generated Base64 Encoded JWT Secret Key: " + encodedKey);
    }
}
