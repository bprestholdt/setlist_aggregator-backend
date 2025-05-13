package com.BP.setlistaggregator.repositories;

import com.BP.setlistaggregator.model.Song;
import org.springframework.data.jpa.repository.JpaRepository;

//interface for CRUD operations on Song entities
public interface SongRepository extends JpaRepository<Song, Long> {
    //future methods possibly needed as we flesh out app
}