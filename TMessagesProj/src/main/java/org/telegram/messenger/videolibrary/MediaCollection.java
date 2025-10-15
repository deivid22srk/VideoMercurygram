package org.telegram.messenger.videolibrary;

import java.util.ArrayList;
import java.util.List;

public class MediaCollection {
    public long id;
    public String title;
    public String description;
    public CollectionType type;
    public String posterPath;
    public long createdAt;
    public long updatedAt;
    
    public List<Season> seasons;
    public Movie movie;
    
    public MediaCollection() {
        seasons = new ArrayList<>();
    }
    
    public int getTotalEpisodes() {
        if (type == CollectionType.SERIES && seasons != null) {
            int total = 0;
            for (Season season : seasons) {
                if (season.episodes != null) {
                    total += season.episodes.size();
                }
            }
            return total;
        }
        return type == CollectionType.MOVIE && movie != null ? 1 : 0;
    }
    
    public int getWatchedEpisodes() {
        if (type == CollectionType.SERIES && seasons != null) {
            int watched = 0;
            for (Season season : seasons) {
                if (season.episodes != null) {
                    for (Episode episode : season.episodes) {
                        if (episode.watchedComplete) {
                            watched++;
                        }
                    }
                }
            }
            return watched;
        }
        return type == CollectionType.MOVIE && movie != null && movie.watchedComplete ? 1 : 0;
    }
    
    public float getWatchProgress() {
        int total = getTotalEpisodes();
        if (total == 0) {
            return 0f;
        }
        return (float) getWatchedEpisodes() / total;
    }
}
