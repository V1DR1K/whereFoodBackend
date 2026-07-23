package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "place_visit_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"visit_id", "author_id"}))
public class PlaceVisitReview {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "visit_id", nullable = false) public PlaceVisit visit;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false) public User author;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 @Column(nullable = false) public short overall;
 @Column(length = 2000) public String comment;
 public Short taste;
 public Short price;
 public Short location;
 public Short heating;
 public Short bathrooms;
 public Short exterior;
 public Short seating;
 public Short service;
 public Short ambiance;
 public Instant createdAt;
 public Instant updatedAt;
}
