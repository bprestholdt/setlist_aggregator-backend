package com.BP.setlistaggregator.repositories;

//import artist entity from models
import com.BP.setlistaggregator.model.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
//for optional return values to handle null results
import java.util.Optional;

/*
extending JpaRepository inherits standard CRUD methods automatically implemented by Spring
we create an interface to handle CRUD operations on Artist entities
repositories are interfaces powered by Spring Data JPA that handles reading and writing to the database
Spring auto-generates SQL code behind the scenes when you extend JPARepository
Also gives methods including findAll(), findById, save(entity), others
*/
public interface ArtistRepository extends JpaRepository<Artist, Long> {

    //method to find an artist given their unique Musicbrainz ID (mbid)
    //optional to handle null returns
    Optional<Artist> findByMbid(String mbid);
    Optional<Artist> findByNameIgnoreCase(String name);
    //Optional<Artist> findAll();

}