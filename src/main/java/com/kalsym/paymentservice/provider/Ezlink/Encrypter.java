/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kalsym.paymentservice.provider.Ezlink;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;

//TODO: Remove Code
/**
 *
 * @author user
 */
public class Encrypter {
    
     
    public static byte[] concatByte(byte[] a, byte[] b) {
        int lenA = a.length;
        int lenB = b.length;
        byte[] c = Arrays.copyOf(a, lenA + lenB);
        System.arraycopy(b, 0, c, lenA, lenB);
        return c;
    }
    
    public static byte[] generateIvByte() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    
    public static IvParameterSpec generateIv(byte[] iv) {
        return new IvParameterSpec(iv);
    }
    
    public static IvParameterSpec getIv(String initVector) throws Exception {
        byte[] iv = Hex.decodeHex(initVector.toCharArray());
        return new IvParameterSpec(iv);
    }
    
    public static SecretKey getKey(String secretKey) {
        // decode the base64 encoded string
        //byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        byte[] decodedKey = secretKey.getBytes();
        // rebuild key using SecretKeySpec
        SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES"); 
        String encodedKey = Base64.getEncoder().encodeToString(originalKey.getEncoded());
        return originalKey;
    }
    
    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /*public static SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey key = keyGenerator.generateKey();
        String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
        System.out.println("Key:"+encodedKey);
        return key;
    }
    
    public static String encrypt(String value, String secretKey) {
        try {            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, getKey(secretKey), generateIv(generateIvByte()));
            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
    public static String encryptWithIV(String value, String secretKey, String IV) {
        try {            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, getKey(secretKey), getIv(IV));
            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
    public static String decrypt(String cipherText, String secretKey, String IV) throws Exception  {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE, getKey(secretKey), getIv(IV));
        byte[] plainText = cipher.doFinal(Base64.getDecoder().decode(cipherText));
        return new String(plainText);
    }*/
    
    
    public static String generateSignature(String value, String secretKey) {
        try {
            byte[] ivByte = generateIvByte();
            IvParameterSpec iv = generateIv(ivByte);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, getKey(secretKey), iv);
            byte[] encrypted = cipher.doFinal(value.getBytes());
            byte[] res = concatByte(encrypted, ivByte);
            return Base64.getEncoder().encodeToString(res);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
    
    public static String generateSignatureWithIV(String value, String secretKey, String IV) {
        try {
            //byte[] ivByte = Hex.decodeHex(IV.toCharArray());
            byte[] ivByte = IV.getBytes();
            IvParameterSpec iv = generateIv(ivByte);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, getKey(secretKey), iv);
            byte[] encrypted = cipher.doFinal(value.getBytes());
            //byte[] res = concatByte(encrypted, ivByte);
            String encodedAmount = Base64.getEncoder().encodeToString(encrypted);
            return hex(encodedAmount.getBytes());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
    public static String generateSignatureWithIV(String value, String secretKey, byte[] ivByte) {
        try {
            //byte[] ivByte = Hex.decodeHex(IV.toCharArray());
            IvParameterSpec iv = generateIv(ivByte);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, getKey(secretKey), iv);
            byte[] encrypted = cipher.doFinal(value.getBytes());
            //byte[] res = concatByte(encrypted, ivByte);
            String encodedAmount = Base64.getEncoder().encodeToString(encrypted);
            return hex(encodedAmount.getBytes());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

}
