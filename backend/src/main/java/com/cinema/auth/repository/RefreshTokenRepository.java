package com.cinema.auth.repository;

import com.cinema.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Shape fixed by TECH.md §3a — refresh tokens are tracked here precisely because a
 * stateless JWT cannot otherwise be revoked before it expires.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    /** "Log out everywhere". */
    @Modifying
    @Query("delete from RefreshToken t where t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    /** Housekeeping for tokens that are already past their expiry. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
