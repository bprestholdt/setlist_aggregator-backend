package com.BP.setlistaggregator.repositories;
import java.time.LocalDate;

import com.BP.setlistaggregator.model.Setlist;
import com.BP.setlistaggregator.model.Artist;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
//data access class directly interacts with postgreSQL database
//uses Spring Data JPA to perform queries, CRUD methods
//interface for CRUD on Setlist entities
public interface SetlistRepository extends JpaRepository<Setlist, Long> {

    //method to retrieve all setlists belonging to an artist using their artist ID
    List<Setlist> findByArtistName(String artist);
    //method to check if a setlist already exists for a given concert and date
    boolean existsByArtistAndDate (Artist artist, LocalDate date);

    List<Setlist> findByArtistNameOrderByDateDesc(String name);

}

