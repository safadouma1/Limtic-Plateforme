package com.projetweb.webapp.web;

import com.projetweb.webapp.ia.IaIndisponibleException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Toutes les erreurs de l'API sont renvoyées sous la forme { "message": "..." }. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IaIndisponibleException.class)
    ResponseEntity<Map<String, String>> ia(IaIndisponibleException e) {
        return erreur(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> introuvable(NoSuchElementException e) {
        return erreur(HttpStatus.NOT_FOUND, e.getMessage() == null ? "Ressource introuvable" : e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return erreur(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrite(DataIntegrityViolationException e) {
        return erreur(HttpStatus.CONFLICT, "Opération impossible : donnée en double ou encore utilisée ailleurs.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> argument(IllegalArgumentException e) {
        return erreur(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, String>> type(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        return erreur(HttpStatus.BAD_REQUEST, "Paramètre invalide : " + e.getName());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> statut(ResponseStatusException e) {
        return erreur(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }

    private static ResponseEntity<Map<String, String>> erreur(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message == null ? status.getReasonPhrase() : message));
    }
}
