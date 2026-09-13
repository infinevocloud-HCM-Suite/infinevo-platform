package com.phegondev.usersmanagementsystem.exceptions.useraccess;

public class ActionNotFoundException extends RuntimeException {
    public ActionNotFoundException(String message) {
        super(message);
    }
}
