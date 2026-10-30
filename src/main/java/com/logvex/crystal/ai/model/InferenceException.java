package com.logvex.crystal.ai.model;

/** Raised when a local model cannot load, or an inference call fails or times out. */
public class InferenceException extends Exception {

    public InferenceException(String message) {
        super(message);
    }

    public InferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
