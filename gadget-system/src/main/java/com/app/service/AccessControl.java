package com.app.service;

import com.app.model.User;

/** Service-layer authorization: the menus hide options, but the services enforce the rules. */
public final class AccessControl {

    private AccessControl() { }

    public static void require(User user, String... allowedRoles) {
        if (user == null) throw new ServiceException("You must be logged in.");
        for (String r : allowedRoles) {
            if (r.equals(user.getRole())) return;
        }
        throw new ServiceException("Access denied: the '" + user.getRole() + "' role cannot perform this action.");
    }
}
