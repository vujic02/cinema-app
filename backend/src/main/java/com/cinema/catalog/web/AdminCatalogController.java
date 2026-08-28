package com.cinema.catalog.web;

import com.cinema.catalog.dto.CatalogImportResponse;
import com.cinema.catalog.service.MovieImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Refreshing the catalogue from TMDB.
 *
 * <p>Admin-only and explicitly triggered rather than run on a schedule or at startup: it is the
 * one operation in the app that reaches out to a third party, and a boot that silently depends
 * on a public API being up (and on a key being present) is a boot that fails in CI for reasons
 * nothing in the codebase explains.
 */
@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: catalog")
public class AdminCatalogController {

    private final MovieImportService movieImport;

    @PostMapping("/import-featured")
    @Operation(summary = "Import this week's trending movies from TMDB and schedule showings for them",
            description = """
                    Idempotent: a movie already imported is updated in place rather than duplicated.
                    With replaceUpcoming=true the existing forward schedule is cleared first, but only
                    showings nobody has booked — sold tickets are never touched.
                    Answers 503 TMDB_NOT_CONFIGURED when no API key is set.
                    """)
    public CatalogImportResponse importFeatured(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "5") int days,
            @RequestParam(defaultValue = "true") boolean replaceUpcoming) {
        return movieImport.importFeatured(limit, days, replaceUpcoming);
    }
}
