package com.cinema.movie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "movies")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, length = 60)
    private String genre;

    @Column(nullable = false, length = 10)
    private String rating;

    /**
     * Hue (0–359) for the poster gradient. Still here now that {@link #posterUrl} exists: it is
     * what the card shows while the image loads, and the whole poster for a movie entered by
     * hand through the admin API that never got artwork.
     */
    @Column(name = "poster_hue", nullable = false)
    @Builder.Default
    private int posterHue = 200;

    /** Fully-qualified artwork URL, or null for a movie with no poster. */
    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    /**
     * TMDB's id for this movie, or null for the seeded and hand-entered ones. Unique where
     * present, which is what lets the importer update a movie it has already seen instead of
     * inserting it again.
     */
    @Column(name = "tmdb_id", unique = true)
    private Long tmdbId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
