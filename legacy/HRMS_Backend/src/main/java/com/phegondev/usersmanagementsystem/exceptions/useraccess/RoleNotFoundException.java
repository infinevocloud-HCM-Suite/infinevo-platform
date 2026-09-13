package com.phegondev.usersmanagementsystem.exceptions.useraccess;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String message) {
        super(message);
    }
}

