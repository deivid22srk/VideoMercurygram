package org.telegram.messenger.videolibrary;

public class Movie {
    public long id;
    public long collectionId;
    
    public int messageId;
    public long chatId;
    public long accessHash;
    public byte[] fileReference;
    
    public int duration;
    public String thumbnailPath;
    public long watchedPosition;
    public boolean watchedComplete;
    public long addedAt;
    
    public float getWatchProgress() {
        if (duration == 0) {
            return watchedComplete ? 1f : 0f;
        }
        return Math.min(1f, (float) watchedPosition / duration);
    }
    
    public String getFormattedDuration() {
        if (duration == 0) {
            return "0:00";
        }
        int minutes = duration / 60;
        int seconds = duration % 60;
        if (minutes >= 60) {
            int hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }
}
