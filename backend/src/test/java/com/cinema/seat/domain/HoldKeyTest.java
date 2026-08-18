package com.cinema.seat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No container needed: the key format is pure string handling, and it is the contract the
 * expiry listener depends on, so it is worth pinning on its own.
 */
class HoldKeyTest {

    @Test
    @DisplayName("the key is hold:{showingId}:{seatId}, as TECH.md §5 specifies")
    void formatsKey() {
        assertThat(new HoldKey(7, 42).format()).isEqualTo("hold:7:42");
    }

    @Test
    @DisplayName("the showing pattern matches that showing's holds and nothing else")
    void formatsScanPattern() {
        assertThat(HoldKey.showingPattern(7)).isEqualTo("hold:7:*");
    }

    @Test
    @DisplayName("a formatted key parses back to the same ids")
    void roundTrips() {
        HoldKey original = new HoldKey(12, 345);
        assertThat(HoldKey.parse(original.format())).contains(original);
    }

    @ParameterizedTest
    @DisplayName("anything that is not one of our keys parses to empty rather than throwing")
    @ValueSource(strings = {
            "",
            "hold:",
            "hold:7",
            "hold:7:42:99",
            "hold:seven:42",
            "hold:7:forty-two",
            "session:7:42",
            "refresh_token:abc"
    })
    void rejectsForeignKeys(String key) {
        // The expiry listener is handed every key that expires in the database, not only ours.
        assertThat(HoldKey.parse(key)).isEmpty();
    }

    @Test
    @DisplayName("a null key is not a crash")
    void rejectsNull() {
        assertThat(HoldKey.parse(null)).isEmpty();
    }
}
