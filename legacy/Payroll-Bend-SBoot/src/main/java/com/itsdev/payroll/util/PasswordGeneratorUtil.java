package com.itsdev.payroll.util;

import java.security.SecureRandom;
import java.util.Random;

public class PasswordGeneratorUtil {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIAL;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate a random password of specified length
     * 
     * @param length length of password (minimum 8)
     * @return generated password
     */
    public static String generatePassword(int length) {
        if (length < 8) {
            length = 8; // Ensure minimum 8 characters
        }

        StringBuilder password = new StringBuilder(length);

        // Ensure at least one character from each category
        password.append(getRandomChar(UPPER));
        password.append(getRandomChar(LOWER));
        password.append(getRandomChar(DIGITS));
        password.append(getRandomChar(SPECIAL));

        // Fill remaining characters randomly from all categories
        for (int i = 4; i < length; i++) {
            password.append(getRandomChar(ALL_CHARS));
        }

        // Shuffle the password to randomize character positions
        return shuffleString(password.toString());
    }

    /**
     * Generate a random password with default length of 12
     */
    public static String generatePassword() {
        return generatePassword(12);
    }

    /**
     * Get a random character from the given character set
     */
    private static char getRandomChar(String characterSet) {
        return characterSet.charAt(RANDOM.nextInt(characterSet.length()));
    }

    /**
     * Shuffle the characters in a string
     */
    private static String shuffleString(String input) {
        char[] characters = input.toCharArray();
        for (int i = characters.length - 1; i > 0; i--) {
            int index = RANDOM.nextInt(i + 1);
            char temp = characters[index];
            characters[index] = characters[i];
            characters[i] = temp;
        }
        return new String(characters);
    }
}