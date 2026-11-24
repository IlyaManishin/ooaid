package app.trackfit.domain.controller;

import java.util.Objects;

public record Outcome<T>(T value, DomainError error) {
    public static <T> Outcome<T> success(T value) {
        return new Outcome<>(value, null);
    }

    public static <T> Outcome<T> failure(DomainError error) {
        return new Outcome<>(null, Objects.requireNonNull(error));
    }

    public boolean succeeded() {
        return error == null;
    }
}
