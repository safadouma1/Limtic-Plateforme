package com.projetweb.webapp.ia;

/** Levée quand Gemini n'est pas configuré ou ne répond pas correctement. Convertie en HTTP 503. */
public class IaIndisponibleException extends RuntimeException {
    public IaIndisponibleException(String message) { super(message); }
    public IaIndisponibleException(String message, Throwable cause) { super(message, cause); }
}
