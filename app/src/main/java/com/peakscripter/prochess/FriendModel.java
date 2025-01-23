package com.peakscripter.prochess;

public class FriendModel {
    private String userId;
    private String username;
    private long rating;
    private long wins;
    private long losses;

    public FriendModel(String userId, String username, long rating, long wins, long losses) {
        this.userId = userId;
        this.username = username;
        this.rating = rating;
        this.wins = wins;
        this.losses = losses;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public long getRating() { return rating; }
    public long getWins() { return wins; }
    public long getLosses() { return losses; }
    public long getGamesPlayed() { return wins + losses; }
    public String getWinRate() {
        if (getGamesPlayed() == 0) return "0%";
        return Math.round((wins * 100.0) / getGamesPlayed()) + "%";
    }
}