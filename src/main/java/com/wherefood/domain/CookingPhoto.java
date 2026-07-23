package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "cooking_photos")
public class CookingPhoto {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cooking_id", nullable = false) public Cooking cooking;
 @Column(columnDefinition = "text", nullable = false) public String imageBase64;
 @Column(columnDefinition = "text", nullable = false) public String thumbnailBase64;
 public int width;
 public int height;
 @Column(nullable = false) public int position;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) public User createdBy;
 public Instant createdAt;
}
