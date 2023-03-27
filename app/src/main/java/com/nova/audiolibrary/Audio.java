package com.nova.audiolibrary;

public class Audio {
    private String title;
    private int lenght;
    private int time;
    private String uri;
    private String artist;

    public Audio(String title, int lenght, int time, String uri, String artist) {
        this.title = title;
        this.lenght = lenght;
        this.time = time;
        this.uri = uri;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getLenght() {
        return lenght;
    }

    public void setLenght(int lenght) {
        this.lenght = lenght;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }
}
