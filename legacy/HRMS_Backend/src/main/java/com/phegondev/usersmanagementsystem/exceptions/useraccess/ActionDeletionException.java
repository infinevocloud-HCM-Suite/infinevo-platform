package com.phegondev.usersmanagementsystem.exceptions.useraccess;

import java.util.List;

public class ActionDeletionException extends RuntimeException {
    private List<String> roleNames;
    private List<String> usernames;

    public ActionDeletionException(String message, List<String> roleNames, List<String> usernames) {
        super(message);
        this.roleNames = roleNames;
        this.usernames = usernames;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public List<String> getUsernames() {
        return usernames;
    }
}

