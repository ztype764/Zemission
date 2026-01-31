package com.ztype.zemmision.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Playlist {
    private String id;
    private String name;
    private String description;
    private List<Track> tracks;
    private String torrentHash;
    private String torrentFilePath; // Path to the .torrent file
    private String coverImagePath;
    private String author;
    private long lastPlayed;
    private boolean isPermanentlySeeded;

    public Playlist() {
        this.id = UUID.randomUUID().toString();
        this.tracks = new ArrayList<>();
        this.lastPlayed = System.currentTimeMillis(); // Default to now on creation
        this.isPermanentlySeeded = false;
    }

    public Playlist(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public void setTracks(List<Track> tracks) {
        this.tracks = tracks;
    }

    public String getTorrentHash() {
        return torrentHash;
    }

    public void setTorrentHash(String torrentHash) {
        this.torrentHash = torrentHash;
    }

    public String getTorrentFilePath() {
        return torrentFilePath;
    }

    public void setTorrentFilePath(String torrentFilePath) {
        this.torrentFilePath = torrentFilePath;
    }

    public String getCoverImagePath() {
        return coverImagePath;
    }

    public void setCoverImagePath(String coverImagePath) {
        this.coverImagePath = coverImagePath;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public boolean isPermanentlySeeded() {
        return isPermanentlySeeded;
    }

    public void setPermanentlySeeded(boolean permanentlySeeded) {
        isPermanentlySeeded = permanentlySeeded;
    }
}
