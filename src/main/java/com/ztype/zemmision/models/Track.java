package com.ztype.zemmision.models;

public class Track {
    private String title;
    private String filePath; // Local path or relative path in torrent
    private long durationSeconds;
    private long sizeBytes;
    private String artist;
    private String album;
    private String coverImagePath;

    public Track() {
    }

    public Track(String title, String filePath, long durationSeconds, long sizeBytes) {
        this.title = title;
        this.filePath = filePath;
        this.durationSeconds = durationSeconds;
        this.sizeBytes = sizeBytes;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getCoverImagePath() {
        return coverImagePath;
    }

    public void setCoverImagePath(String coverImagePath) {
        this.coverImagePath = coverImagePath;
    }
}
