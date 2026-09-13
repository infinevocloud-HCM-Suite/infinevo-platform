package com.phegondev.usersmanagementsystem.exceptions.useraccess;

public class ActionAlreadyExistsException extends RuntimeException {
    public ActionAlreadyExistsException(String message) {
        super(message);
    }
}
