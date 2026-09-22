package com.BP.setlistaggregator.repositories;
import java.time.LocalDate;

import com.BP.setlistaggregator.model.Setlist;
import com.BP.setlistaggregator.model.Artist;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Set;
//data access class directly interacts with postgreSQL database
//uses Spring Data JPA to perform queries, CRUD methods
//interface for CRUD on Setlist entities
public interface SetlistRepository extends JpaRepository<Setlist, Long> {

    //method to retrieve all setlists belonging to an artist, newest first
    //looks up by the artist entity (not the name string) so capitalization differences can't miss rows
    //@EntityGraph loads each setlist's songs in the same query instead of one extra query per setlist
    @EntityGraph(attributePaths = "songs")
    List<Setlist> findByArtistOrderByDateDesc(Artist artist);

    //which of these setlist.fm ids are already saved
    @Query("select s.setlistfmId from Setlist s where s.setlistfmId in :ids")
    Set<String> findExistingSetlistfmIds(@Param("ids") Collection<String> ids);

    //dates of an artist's setlists saved before setlist.fm ids were stored, for matching those older rows
    @Query("select s.date from Setlist s where s.artist = :artist and s.setlistfmId is null")
    Set<LocalDate> findDatesWithoutSetlistfmId(@Param("artist") Artist artist);
}
