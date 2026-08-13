package com.cinema.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * There is deliberately no {@code role} field. Self-service registration always produces a
 * CUSTOMER; admins are created by migration.
 */
public record RegisterRequest(

        @NotBlank
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        String email,

        // Upper bound is 72 because BCrypt silently truncates anything longer, which would make
        // two different long passwords interchangeable.
        @NotBlank
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        @NotBlank
        @Size(max = 120)
        String fullName
) {
}
