package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "recipes")
public class Recipe {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Column(nullable = false) public String name;
 @Column(name = "source_url") public String sourceUrl;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) public User createdBy;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("position asc") public List<RecipeIngredient> ingredients = new ArrayList<>();
 @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("position asc") public List<RecipeStep> steps = new ArrayList<>();
 public Instant createdAt;
 public Instant updatedAt;
}
