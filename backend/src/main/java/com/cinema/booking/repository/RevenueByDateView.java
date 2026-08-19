package com.cinema.booking.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection interface (TECH.md §3a tier 4) for the dashboard's revenue chart.
 * <p>
 * Keyed on the day the purchase was made, not the day of the screening — revenue is recognised
 * when the ticket is sold. The per-showing view is the other axis.
 */
public interface RevenueByDateView {

    LocalDate getDate();

    long getTicketsSold();

    BigDecimal getRevenue();
}
