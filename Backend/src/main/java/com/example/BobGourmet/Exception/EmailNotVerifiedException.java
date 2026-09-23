package com.example.BobGourmet.Exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Please verify your email before signing up.");
    }
}
