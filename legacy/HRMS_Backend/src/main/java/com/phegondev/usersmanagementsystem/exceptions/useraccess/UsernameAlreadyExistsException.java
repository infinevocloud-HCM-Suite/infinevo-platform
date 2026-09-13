package com.phegondev.usersmanagementsystem.exceptions.useraccess;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String message) {
        super(message);
    }
}
