package com.cinema.catalog.tmdb;

import com.cinema.common.exception.ApiException;
import com.cinema.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * The only class that talks to themoviedb.org.
 *
 * <p>Everything it can go wrong with is turned into an {@link ApiException} with a code the
 * frontend could branch on, because "the catalogue could not be refreshed" is an operational
 * answer an admin can act on, while a raw {@code RestClientException} surfacing as a 500 is not.
 *
 * <p>No caching and no retry. An import is a deliberate, occasional admin action; retrying a
 * rate-limited third party inside a request thread would turn one slow call into several.
 */
@Component
@Slf4j
public class TmdbClient {

    /** TMDB is a third party on the public internet; a hung call must not pin a request thread. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final AppProperties.Tmdb config;
    private final RestClient http;

    public TmdbClient(AppProperties properties, RestClient.Builder builder) {
        this.config = properties.tmdb();

        RestClient.Builder configured = builder
                .baseUrl(config.baseUrl())
                .requestFactory(timeouts());

        // A v4 read access token authenticates by header; a v3 key by query parameter. Setting
        // the header once here keeps every call site free of the distinction.
        if (config.bearerToken()) {
            configured = configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey());
        }
        this.http = configured.build();
    }

    /**
     * TMDB's own "what people are watching" chart — the closest thing it has to a featured list,
     * and unlike {@code /movie/popular} it turns over weekly rather than being dominated by the
     * same evergreen blockbusters.
     *
     * <p>Returns the summary shape: no runtime, no genre names, no certification. Those need the
     * detail call, which is why the importer makes one per movie.
     */
    public List<TmdbMovie> trendingThisWeek() {
        requireConfigured();
        Page page = get("/trending/movie/week", uri -> uri, Page.class);
        return page == null || page.results() == null ? List.of() : page.results();
    }

    /**
     * One movie, with runtime, genres and certifications.
     *
     * <p>{@code append_to_response} folds the {@code /release_dates} sub-resource into this same
     * response — TMDB's mechanism for exactly this, and it halves the number of round trips an
     * import makes.
     */
    public TmdbMovie detail(long tmdbId) {
        requireConfigured();
        return get("/movie/" + tmdbId,
                uri -> uri.queryParam("append_to_response", "release_dates"),
                TmdbMovie.class);
    }

    private <T> T get(String path, Function<UriBuilder, UriBuilder> extraParams, Class<T> type) {
        try {
            return http.get()
                    .uri(uriBuilder -> {
                        UriBuilder uri = uriBuilder.path(path).queryParam("language", config.language());
                        // Only a v3 key travels in the query string; a bearer token is already
                        // on the request as a header and must not be echoed into the URL.
                        if (!config.bearerToken()) {
                            uri = uri.queryParam("api_key", config.apiKey());
                        }
                        return extraParams.apply(uri).build();
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 401 || status.value() == 403,
                            (request, response) -> {
                                throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAUTHORIZED",
                                        "TMDB rejected the API key. Check app.tmdb.api-key / TMDB_API_KEY.");
                            })
                    .onStatus(status -> status.value() == 429,
                            (request, response) -> {
                                throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_RATE_LIMITED",
                                        "TMDB is rate limiting this key. Try again in a minute.");
                            })
                    .body(type);
        } catch (ApiException alreadyExplained) {
            throw alreadyExplained;
        } catch (RestClientException failed) {
            log.error("TMDB call to {} failed: {}", path, failed.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE",
                    "Could not reach TMDB. The catalogue was left unchanged.");
        }
    }

    private void requireConfigured() {
        if (!config.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB_NOT_CONFIGURED",
                    "No TMDB API key is configured. Set the TMDB_API_KEY environment variable.");
        }
    }

    private static org.springframework.http.client.ClientHttpRequestFactory timeouts() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return factory;
    }

    /** TMDB paginates everything, including charts. Only the first page is ever needed here. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page(int page, List<TmdbMovie> results) {
    }
}
