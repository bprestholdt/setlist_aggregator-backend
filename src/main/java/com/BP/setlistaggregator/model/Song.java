package com.BP.setlistaggregator.model;

import jakarta.persistence.*;
import java.util.List;
import com.BP.setlistaggregator.model.Setlist;
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

    //many songs in any one setlist
    @ManyToOne
    //foreign key in song table to link to setlist table
    @JoinColumn(name = "setlist_id")
    private Setlist setlist;

    public Song() {

    }
    //building song entity used in mapApiSetlistToEntity method in fetch service
    public Song(String title, int position, Setlist setlist) {
        this.title = title;
        this.position = position;
        this.setlist = setlist;
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
