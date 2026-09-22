package com.BP.setlistaggregator.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;

//represents a single concert in the database
@Entity
public class Setlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //setlist.fm's own id for this setlist, used to detect duplicates (null for setlists saved before this was added)
    @Column(name = "setlistfm_id")
    private String setlistfmId;

    //variables to hold info about specific setlist
    private LocalDate date;
    //venue name, e.g. "Soldier Field"
    private String venue;
    //display label: "Chicago, IL" for US shows, "Paris, France" elsewhere
    private String city;
    //country name, e.g. "United States"
    private String country;
    //tour name if the show was part of a named tour
    private String tour;

    //Many setlists can be assigned to one artist
    @ManyToOne
    //foreign key to link artist
    @JoinColumn(name = "artist_id")
    //maps foreign key column to artist table
    private Artist artist;

    //one setlist can have many songs
    @OneToMany(mappedBy = "setlist", cascade = CascadeType.ALL)
    private List<Song> songs;

    //default constructor
    public Setlist() {

    }

    //getters and setters, allow service layer and controller to modify setlist objects

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSetlistfmId() {
        return setlistfmId;
    }

    public void setSetlistfmId(String setlistfmId) {
        this.setlistfmId = setlistfmId;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getTour() {
        return tour;
    }

    public void setTour(String tour) {
        this.tour = tour;
    }

    public Artist getArtist() {
        return artist;
    }

    public void setArtist(Artist artist) {
        this.artist = artist;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    //for logging
    @Override
    public String toString() {
        return "Setlist{" +
                "date=" + date +
                ", venue='" + venue + '\'' +
                ", artist=" + (artist != null ? artist.getName() : "null") +
                '}';
    }
}