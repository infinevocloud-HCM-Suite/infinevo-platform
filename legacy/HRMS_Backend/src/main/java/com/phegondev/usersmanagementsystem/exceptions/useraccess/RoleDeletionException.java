package com.phegondev.usersmanagementsystem.exceptions.useraccess;

import java.util.List;

public class RoleDeletionException extends RuntimeException {
    private final List<String> associatedActions;
    private final List<String> associatedUsers;

    public RoleDeletionException(String message, List<String> associatedActions, 
                                  List<String> associatedUsers) {
        super(message);
        this.associatedActions = associatedActions;       
        this.associatedUsers = associatedUsers;
    }

    public List<String> getAssociatedActions() {
        return associatedActions;
    }

    public List<String> getAssociatedUsers() {
        return associatedUsers;
    }
}

