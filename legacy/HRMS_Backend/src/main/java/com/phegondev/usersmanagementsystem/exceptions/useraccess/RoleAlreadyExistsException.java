package com.phegondev.usersmanagementsystem.exceptions.useraccess;

public class RoleAlreadyExistsException extends RuntimeException {
    public RoleAlreadyExistsException(String message) {
        super(message);
    }
}

