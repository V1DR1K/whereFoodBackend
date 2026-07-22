package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "film_views")
public class FilmView {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "film_id") public Film film;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") public User createdBy;
 @Column(nullable = false) public LocalDate watchedOn;
 @Column(name = "watched_at") public LocalTime watchedAt;
 public Instant createdAt;
}
