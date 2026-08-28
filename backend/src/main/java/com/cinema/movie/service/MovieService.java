package com.cinema.movie.service;

import com.cinema.common.exception.ConflictException;
import com.cinema.common.exception.NotFoundException;
import com.cinema.movie.domain.Movie;
import com.cinema.movie.dto.MovieRequest;
import com.cinema.movie.dto.MovieResponse;
import com.cinema.movie.repository.MovieRepository;
import com.cinema.showing.repository.ShowingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movies;
    private final ShowingRepository showings;

    @Transactional(readOnly = true)
    public List<MovieResponse> findAll(String titleFragment) {
        List<Movie> found = (titleFragment == null || titleFragment.isBlank())
                ? movies.findAllByOrderByTitleAsc()
                : movies.findByTitleContainingIgnoreCase(titleFragment.trim());
        return found.stream().map(MovieResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MovieResponse findById(Long id) {
        return MovieResponse.from(require(id));
    }

    @Transactional
    public MovieResponse create(MovieRequest request) {
        return MovieResponse.from(movies.save(apply(new Movie(), request)));
    }

    @Transactional
    public MovieResponse update(Long id, MovieRequest request) {
        return MovieResponse.from(movies.save(apply(require(id), request)));
    }

    @Transactional
    public void delete(Long id) {
        Movie movie = require(id);
        // showings.movie_id is a plain foreign key; deleting underneath it would fail at the
        // database with an opaque error instead of an explainable one.
        if (showings.existsByMovieId(id)) {
            throw new ConflictException("MOVIE_HAS_SHOWINGS",
                    "This movie still has showings scheduled. Delete those first.");
        }
        movies.delete(movie);
    }

    private Movie apply(Movie movie, MovieRequest request) {
        movie.setTitle(request.title().trim());
        movie.setDescription(request.description() == null ? null : request.description().trim());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setGenre(request.genre().trim());
        movie.setRating(request.rating().trim());
        movie.setPosterHue(request.posterHue());
        // Blank and absent mean the same thing here: no artwork, fall back to the gradient.
        String posterUrl = request.posterUrl() == null ? null : request.posterUrl().trim();
        movie.setPosterUrl(posterUrl == null || posterUrl.isEmpty() ? null : posterUrl);
        return movie;
    }

    private Movie require(Long id) {
        return movies.findById(id).orElseThrow(() -> new NotFoundException("Movie", id));
    }
}
