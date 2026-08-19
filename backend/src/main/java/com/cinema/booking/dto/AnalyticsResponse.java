package com.cinema.booking.dto;

import com.cinema.booking.repository.BookingsPerShowingView;
import com.cinema.booking.repository.RevenueByDateView;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The admin dashboard: the four counters across the top, then the two series underneath.
 * Assembled from tier-4 projections (TECH.md §3a) rather than from entities — nothing here is
 * written back, and totalling a column should not hydrate a row per ticket.
 */
public record AnalyticsResponse(
        Totals totals,
        List<ShowingSales> topShowings,
        List<DailyRevenue> revenueByDate
) {

    /**
     * @param purchases distinct booking references — three seats on one reference is one sale
     * @param tickets   individual seats sold, which is what fills an auditorium
     */
    public record Totals(long purchases, long tickets, BigDecimal revenue, long upcomingShowings) {
    }

    public record ShowingSales(
            Long showingId,
            String movieTitle,
            String venueName,
            Instant startTime,
            long seatsSold,
            BigDecimal revenue
    ) {
        static ShowingSales from(BookingsPerShowingView view) {
            return new ShowingSales(
                    view.getShowingId(),
                    view.getMovieTitle(),
                    view.getVenueName(),
                    view.getStartTime(),
                    view.getSeatsSold(),
                    view.getRevenue());
        }
    }

    public record DailyRevenue(LocalDate date, long ticketsSold, BigDecimal revenue) {
        static DailyRevenue from(RevenueByDateView view) {
            return new DailyRevenue(view.getDate(), view.getTicketsSold(), view.getRevenue());
        }
    }

    public static AnalyticsResponse of(Totals totals,
                                       List<BookingsPerShowingView> showings,
                                       List<RevenueByDateView> revenue) {
        return new AnalyticsResponse(
                totals,
                showings.stream().map(ShowingSales::from).toList(),
                revenue.stream().map(DailyRevenue::from).toList());
    }
}
