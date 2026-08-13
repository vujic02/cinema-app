package com.cinema.auth.dto;

import com.cinema.auth.domain.Role;
import com.cinema.auth.domain.User;

/** The public view of a user. Never carries {@code passwordHash}. */
public record UserResponse(Long id, String email, String fullName, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
