package com.BP.setlistaggregator.model;

import jakarta.persistence.*;
import java.util.List;
import com.BP.setlistaggregator.model.Setlist;
import com.fasterxml.jackson.annotation.JsonIgnore;

//marks class as JPA entity, enabling spring boot to map it to a table in PostgreSQL database
//creates "artist" class in database
@Entity
//each Artist instance will be 1 row in artist table
public class Artist {
    //primary key
    @Id
    //auto generate IDs for artists (separate from mbid used by Setlist.fm)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    //musicbrainz id needed for matching with setlist.fm api
    private String mbid;
    //whether we've already fetched all available setlists from the API for this artist
    private boolean fullyFetched = false;

    //one artist can have many setlists
    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL)
    //prevent recursive serialization
    @JsonIgnore
    //list to hold artists setlists
    private List<Setlist> setlists;

    //getters setters and constructors

    //default constructor
    public Artist() {}

    //paramaterized constructor
    public Artist(String name, String mbid) {
        this.name = name;
        this.mbid = mbid;
    }

    //getters
    public Long getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getMbid() {
        return mbid;
    }

    public List<Setlist> getSetlists() {
        return setlists;
    }
    //setters
    public void setName(String name) {
        this.name = name;
    }
    public void setMbid(String mbid) {
        this.mbid = mbid;
    }
    public void setSetlists(List<Setlist> setlists) {
        this.setlists = setlists;
}

    //toString for debugging/logging
    @Override
    public String toString() {

        return "Artist{id=" + id + ", name= '" + name + "'. mbid = '" + mbid + "'}";

    }
    public boolean isFullyFetched() {
        return fullyFetched;
    }

    public void setFullyFetched(boolean fullyFetched) {
        this.fullyFetched = fullyFetched;
    }


}