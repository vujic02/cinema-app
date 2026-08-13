package com.cinema.showing.service;

import com.cinema.booking.repository.BookingRepository;
import com.cinema.common.exception.BadRequestException;
import com.cinema.common.exception.ConflictException;
import com.cinema.common.exception.NotFoundException;
import com.cinema.movie.domain.Movie;
import com.cinema.movie.repository.MovieRepository;
import com.cinema.showing.domain.Showing;
import com.cinema.showing.dto.ShowingRequest;
import com.cinema.showing.dto.ShowingResponse;
import com.cinema.showing.repository.ShowingRepository;
import com.cinema.showing.repository.ShowingSpecifications;
import com.cinema.venue.domain.Venue;
import com.cinema.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowingService {

    private static final Sort BY_START_TIME = Sort.by(Sort.Direction.ASC, "startTime");

    private final ShowingRepository showings;
    private final MovieRepository movies;
    private final VenueRepository venues;
    private final BookingRepository bookings;

    /**
     * The customer-facing listing. With no {@code date} it returns everything still to come;
     * with one it returns that single day.
     * <p>
     * Days are UTC boundaries, matching {@code hibernate.jdbc.time_zone=UTC}. A cinema in a
     * non-UTC timezone would want the venue's zone here instead — noted as an open item.
     */
    @Transactional(readOnly = true)
    public List<ShowingResponse> findPublic(LocalDate date, Long venueId, Long movieId) {
        Instant from = date == null ? Instant.now() : date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return search(from, to, venueId, movieId);
    }

    /** The admin schedule screen: an explicit date range rather than a single day. */
    @Transactional(readOnly = true)
    public List<ShowingResponse> findForAdmin(LocalDate from, LocalDate to, Long venueId, Long movieId) {
        return search(
                from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                // Inclusive end date: the caller asking for "to 5 March" means all of 5 March.
                to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                venueId,
                movieId);
    }

    private List<ShowingResponse> search(Instant from, Instant to, Long venueId, Long movieId) {
        return showings.findAll(
                        ShowingSpecifications.filter(
                                ShowingSpecifications.startsAtOrAfter(from),
                                ShowingSpecifications.startsBefore(to),
                                ShowingSpecifications.atVenue(venueId),
                                ShowingSpecifications.ofMovie(movieId)),
                        BY_START_TIME)
                .stream()
                .map(ShowingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShowingResponse findById(Long id) {
        return showings.findWithMovieAndVenueById(id)
                .map(ShowingResponse::from)
                .orElseThrow(() -> new NotFoundException("Showing", id));
    }

    @Transactional
    public ShowingResponse create(ShowingRequest request) {
        if (request.startTime().isBefore(Instant.now())) {
            throw new BadRequestException("SHOWING_IN_THE_PAST", "A new showing cannot start in the past");
        }
        requireSlotFree(request.venueId(), request.startTime(), null);

        Showing showing = showings.save(Showing.builder()
                .movie(requireMovie(request.movieId()))
                .venue(requireVenue(request.venueId()))
                .startTime(request.startTime())
                .price(request.price())
                .build());

        return ShowingResponse.from(showing);
    }

    @Transactional
    public ShowingResponse update(Long id, ShowingRequest request) {
        Showing showing = showings.findById(id).orElseThrow(() -> new NotFoundException("Showing", id));
        requireSlotFree(request.venueId(), request.startTime(), id);

        showing.setMovie(requireMovie(request.movieId()));
        showing.setVenue(requireVenue(request.venueId()));
        showing.setStartTime(request.startTime());
        showing.setPrice(request.price());

        return ShowingResponse.from(showings.save(showing));
    }

    @Transactional
    public void delete(Long id) {
        Showing showing = showings.findById(id).orElseThrow(() -> new NotFoundException("Showing", id));
        // Tickets have been issued against this showing. Deleting it would strand them, and the
        // customer's booking history would point at nothing.
        if (bookings.existsByShowingId(id)) {
            throw new ConflictException("SHOWING_HAS_BOOKINGS",
                    "This showing already has bookings and can no longer be deleted");
        }
        showings.delete(showing);
    }

    /** Enforces {@code uq_showings_venue_start} — one auditorium cannot run two films at once. */
    private void requireSlotFree(Long venueId, Instant startTime, Long selfId) {
        boolean taken = selfId == null
                ? showings.existsByVenueIdAndStartTime(venueId, startTime)
                : showings.existsByVenueIdAndStartTimeAndIdNot(venueId, startTime, selfId);
        if (taken) {
            throw new ConflictException("SHOWING_SLOT_TAKEN",
                    "That venue already has a showing starting at " + startTime);
        }
    }

    private Movie requireMovie(Long id) {
        return movies.findById(id).orElseThrow(() -> new NotFoundException("Movie", id));
    }

    private Venue requireVenue(Long id) {
        return venues.findById(id).orElseThrow(() -> new NotFoundException("Venue", id));
    }
}
