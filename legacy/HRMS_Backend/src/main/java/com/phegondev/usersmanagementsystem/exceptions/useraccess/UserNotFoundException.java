package com.phegondev.usersmanagementsystem.exceptions.useraccess;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
