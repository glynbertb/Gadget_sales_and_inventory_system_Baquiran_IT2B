package com.app.service;

/** Business-rule violation whose message is safe to show to the console user. */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ServiceException(String message) { super(message); }
}
