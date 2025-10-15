package org.telegram.messenger.videolibrary;

import java.util.ArrayList;
import java.util.List;

public class Season {
    public long id;
    public long collectionId;
    public int seasonNumber;
    public String title;
    public long createdAt;
    
    public List<Episode> episodes;
    
    public Season() {
        episodes = new ArrayList<>();
    }
    
    public String getDisplayTitle() {
        if (title != null && !title.isEmpty()) {
            return title;
        }
        return "Season " + seasonNumber;
    }
}
