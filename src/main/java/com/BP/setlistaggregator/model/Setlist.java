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

    //variables to hold info about specific setlist
    private LocalDate date;
    private String venue;
    private String city;
    private String country;

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
    //paramaterized constructor
    //helps with manual setlist construction in SetlistFMFetchService
    public Setlist(LocalDate date, String venue, String city, String country, Artist artist) {
        this.date = date;
        this.venue = venue;
        this.city = city;
        this.country = country;
        this.artist = artist;
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