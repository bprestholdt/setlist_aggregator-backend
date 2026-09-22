package com.BP.setlistaggregator.model;

import jakarta.persistence.*;
//represents a single song in a setlist
@Entity
public class Song {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //variables unique to Song object
    private String title;
    //numerical position in setlist
    private int position;

    //true for recorded intros/outros (tapes); left out of every stat
    //Boolean (not boolean) because rows saved before this column existed are null
    private Boolean tape;

    //true if the song was played in an encore section
    private Boolean encore;

    //original artist if the song is a cover, otherwise null
    @Column(name = "cover_artist")
    private String coverArtist;

    //many songs in any one setlist
    @ManyToOne
    //foreign key in song table to link to setlist table
    @JoinColumn(name = "setlist_id")
    private Setlist setlist;

    public Song() {

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isTape() {
        return Boolean.TRUE.equals(tape);
    }

    public void setTape(Boolean tape) {
        this.tape = tape;
    }

    public boolean isEncore() {
        return Boolean.TRUE.equals(encore);
    }

    public void setEncore(Boolean encore) {
        this.encore = encore;
    }

    public String getCoverArtist() {
        return coverArtist;
    }

    public void setCoverArtist(String coverArtist) {
        this.coverArtist = coverArtist;
    }

    public Setlist getSetlist() {
        return setlist;
    }

    public void setSetlist(Setlist setlist) {
        this.setlist = setlist;
    }

    @Override
    public String toString() {
        return "Song{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", position=" + position +
                ", setlistDate=" + (setlist != null ? setlist.getDate() : "null") +
                '}';
    }
}
