package com.cinema.auth.domain;

/**
 * TECH.md §2. A venue-scoped MANAGER role can be added later without rework — it
 * would simply carry a narrower set of permissions than ADMIN.
 */
public enum Role {
    ADMIN,
    CUSTOMER
}
