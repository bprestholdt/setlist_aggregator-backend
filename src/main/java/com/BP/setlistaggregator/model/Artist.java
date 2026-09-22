package com.BP.setlistaggregator.model;

import jakarta.persistence.*;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;

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
    @Column(name = "fully_fetched")
    private boolean fullyFetched = false;

    //field to track latest fetch- so we can only fetch setlists we don't have once we've fetched all for an artist
    @Column(name = "last_fetched_date")
    private LocalDate lastFetchedDate;

    //date we last asked setlist.fm for new concerts (separate from lastFetchedDate, which is the newest concert's date)
    @Column(name = "last_checked_date")
    private LocalDate lastCheckedDate;

    //bump this when we start saving new setlist details, so older cached setlists get re-fetched with them
    //version 2 = tapes, covers, encore sections, venue/city/country, and tour are saved
    public static final int CURRENT_DATA_VERSION = 2;

    //which version of the saved setlist details this artist's cached setlists have
    //new artists start at the current version; artists saved before this column existed load as null
    @Column(name = "data_version")
    private Integer dataVersion = CURRENT_DATA_VERSION;

    //one artist can have many setlists
    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL)
    //prevent recursive serialization
    @JsonIgnore
    //list to hold artists setlists
    private List<Setlist> setlists;

    //getters setters and constructors

    //default constructor
    public Artist() {}

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
    public LocalDate getLastFetchedDate() {
        return lastFetchedDate;
    }

    public void setLastFetchedDate(LocalDate lastFetchedDate) {
        this.lastFetchedDate = lastFetchedDate;
    }

    //true if this artist's cached setlists are missing details newer versions of the app save
    public boolean hasOutdatedData() {
        return dataVersion == null || dataVersion < CURRENT_DATA_VERSION;
    }

    public Integer getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(Integer dataVersion) {
        this.dataVersion = dataVersion;
    }

    public LocalDate getLastCheckedDate() {
        return lastCheckedDate;
    }

    public void setLastCheckedDate(LocalDate lastCheckedDate) {
        this.lastCheckedDate = lastCheckedDate;
    }
}