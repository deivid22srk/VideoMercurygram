package org.telegram.messenger.videolibrary;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaLibraryStorage {
    
    private SQLiteDatabase database;
    
    public MediaLibraryStorage(SQLiteDatabase database) {
        this.database = database;
    }
    
    public void createTables() throws SQLiteException {
        database.executeFast("CREATE TABLE IF NOT EXISTS media_collections (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "description TEXT, " +
                "type TEXT NOT NULL, " +
                "poster_path TEXT, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)").stepThis().dispose();
        
        database.executeFast("CREATE TABLE IF NOT EXISTS seasons (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "collection_id INTEGER NOT NULL, " +
                "season_number INTEGER NOT NULL, " +
                "title TEXT, " +
                "created_at INTEGER NOT NULL, " +
                "FOREIGN KEY (collection_id) REFERENCES media_collections(id) ON DELETE CASCADE)").stepThis().dispose();
        
        database.executeFast("CREATE TABLE IF NOT EXISTS episodes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "season_id INTEGER NOT NULL, " +
                "episode_number INTEGER NOT NULL, " +
                "title TEXT, " +
                "message_id INTEGER NOT NULL, " +
                "chat_id INTEGER NOT NULL, " +
                "access_hash INTEGER NOT NULL, " +
                "file_reference BLOB, " +
                "duration INTEGER DEFAULT 0, " +
                "thumbnail_path TEXT, " +
                "watched_position INTEGER DEFAULT 0, " +
                "watched_complete INTEGER DEFAULT 0, " +
                "added_at INTEGER NOT NULL, " +
                "FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE)").stepThis().dispose();
        
        database.executeFast("CREATE TABLE IF NOT EXISTS movies (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "collection_id INTEGER NOT NULL, " +
                "message_id INTEGER NOT NULL, " +
                "chat_id INTEGER NOT NULL, " +
                "access_hash INTEGER NOT NULL, " +
                "file_reference BLOB, " +
                "duration INTEGER DEFAULT 0, " +
                "thumbnail_path TEXT, " +
                "watched_position INTEGER DEFAULT 0, " +
                "watched_complete INTEGER DEFAULT 0, " +
                "added_at INTEGER NOT NULL, " +
                "FOREIGN KEY (collection_id) REFERENCES media_collections(id) ON DELETE CASCADE)").stepThis().dispose();
        
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_seasons_collection ON seasons(collection_id)").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_episodes_season ON episodes(season_id)").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_movies_collection ON movies(collection_id)").stepThis().dispose();
    }
    
    public long createCollection(String title, CollectionType type, String description) {
        long id = -1;
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            SQLitePreparedStatement state = database.executeFast("INSERT INTO media_collections (title, type, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)");
            state.requery();
            state.bindString(1, title);
            state.bindString(2, type.name().toLowerCase());
            state.bindString(3, description != null ? description : "");
            state.bindLong(4, currentTime);
            state.bindLong(5, currentTime);
            state.step();
            
            SQLiteCursor cursor = database.queryFinalized("SELECT last_insert_rowid()");
            if (cursor.next()) {
                id = cursor.longValue(0);
            }
            cursor.dispose();
            state.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return id;
    }
    
    public boolean updateCollection(long collectionId, String title, String description, String posterPath) {
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            SQLitePreparedStatement state = database.executeFast("UPDATE media_collections SET title = ?, description = ?, poster_path = ?, updated_at = ? WHERE id = ?");
            state.requery();
            state.bindString(1, title);
            state.bindString(2, description != null ? description : "");
            state.bindString(3, posterPath != null ? posterPath : "");
            state.bindLong(4, currentTime);
            state.bindLong(5, collectionId);
            state.step();
            state.dispose();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
    
    public boolean deleteCollection(long collectionId) {
        try {
            SQLitePreparedStatement state = database.executeFast("DELETE FROM media_collections WHERE id = ?");
            state.requery();
            state.bindLong(1, collectionId);
            state.step();
            state.dispose();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
    
    public List<MediaCollection> getCollections() {
        List<MediaCollection> collections = new ArrayList<>();
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT id, title, description, type, poster_path, created_at, updated_at FROM media_collections ORDER BY updated_at DESC");
            while (cursor.next()) {
                MediaCollection collection = new MediaCollection();
                collection.id = cursor.longValue(0);
                collection.title = cursor.stringValue(1);
                collection.description = cursor.stringValue(2);
                collection.type = CollectionType.valueOf(cursor.stringValue(3).toUpperCase());
                collection.posterPath = cursor.stringValue(4);
                collection.createdAt = cursor.longValue(5);
                collection.updatedAt = cursor.longValue(6);
                collections.add(collection);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return collections;
    }
    
    public MediaCollection getCollection(long collectionId) {
        MediaCollection collection = null;
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT id, title, description, type, poster_path, created_at, updated_at FROM media_collections WHERE id = ?", collectionId);
            if (cursor.next()) {
                collection = new MediaCollection();
                collection.id = cursor.longValue(0);
                collection.title = cursor.stringValue(1);
                collection.description = cursor.stringValue(2);
                collection.type = CollectionType.valueOf(cursor.stringValue(3).toUpperCase());
                collection.posterPath = cursor.stringValue(4);
                collection.createdAt = cursor.longValue(5);
                collection.updatedAt = cursor.longValue(6);
                
                if (collection.type == CollectionType.SERIES) {
                    collection.seasons = getSeasons(collectionId);
                } else if (collection.type == CollectionType.MOVIE) {
                    collection.movie = getMovie(collectionId);
                }
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return collection;
    }
    
    public long addSeason(long collectionId, int seasonNumber, String title) {
        long id = -1;
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            SQLitePreparedStatement state = database.executeFast("INSERT INTO seasons (collection_id, season_number, title, created_at) VALUES (?, ?, ?, ?)");
            state.requery();
            state.bindLong(1, collectionId);
            state.bindInteger(2, seasonNumber);
            state.bindString(3, title != null ? title : "");
            state.bindLong(4, currentTime);
            state.step();
            
            SQLiteCursor cursor = database.queryFinalized("SELECT last_insert_rowid()");
            if (cursor.next()) {
                id = cursor.longValue(0);
            }
            cursor.dispose();
            state.dispose();
            
            updateCollectionTimestamp(collectionId);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return id;
    }
    
    public boolean deleteSeason(long seasonId) {
        try {
            SQLitePreparedStatement state = database.executeFast("DELETE FROM seasons WHERE id = ?");
            state.requery();
            state.bindLong(1, seasonId);
            state.step();
            state.dispose();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
    
    public List<Season> getSeasons(long collectionId) {
        List<Season> seasons = new ArrayList<>();
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT id, collection_id, season_number, title, created_at FROM seasons WHERE collection_id = ? ORDER BY season_number ASC", collectionId);
            while (cursor.next()) {
                Season season = new Season();
                season.id = cursor.longValue(0);
                season.collectionId = cursor.longValue(1);
                season.seasonNumber = cursor.intValue(2);
                season.title = cursor.stringValue(3);
                season.createdAt = cursor.longValue(4);
                season.episodes = getEpisodes(season.id);
                seasons.add(season);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return seasons;
    }
    
    public long addEpisode(long seasonId, int episodeNumber, String title, int messageId, long chatId, long accessHash, byte[] fileReference, int duration) {
        long id = -1;
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            SQLitePreparedStatement state = database.executeFast("INSERT INTO episodes (season_id, episode_number, title, message_id, chat_id, access_hash, file_reference, duration, added_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            state.requery();
            state.bindLong(1, seasonId);
            state.bindInteger(2, episodeNumber);
            state.bindString(3, title != null ? title : "");
            state.bindInteger(4, messageId);
            state.bindLong(5, chatId);
            state.bindLong(6, accessHash);
            if (fileReference != null) {
                state.bindByteBuffer(7, fileReference);
            } else {
                state.bindNull(7);
            }
            state.bindInteger(8, duration);
            state.bindLong(9, currentTime);
            state.step();
            
            SQLiteCursor cursor = database.queryFinalized("SELECT last_insert_rowid()");
            if (cursor.next()) {
                id = cursor.longValue(0);
            }
            cursor.dispose();
            state.dispose();
            
            long collectionId = getCollectionIdFromSeason(seasonId);
            if (collectionId != -1) {
                updateCollectionTimestamp(collectionId);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return id;
    }
    
    public boolean deleteEpisode(long episodeId) {
        try {
            SQLitePreparedStatement state = database.executeFast("DELETE FROM episodes WHERE id = ?");
            state.requery();
            state.bindLong(1, episodeId);
            state.step();
            state.dispose();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
    
    public List<Episode> getEpisodes(long seasonId) {
        List<Episode> episodes = new ArrayList<>();
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT id, season_id, episode_number, title, message_id, chat_id, access_hash, file_reference, duration, thumbnail_path, watched_position, watched_complete, added_at FROM episodes WHERE season_id = ? ORDER BY episode_number ASC", seasonId);
            while (cursor.next()) {
                Episode episode = new Episode();
                episode.id = cursor.longValue(0);
                episode.seasonId = cursor.longValue(1);
                episode.episodeNumber = cursor.intValue(2);
                episode.title = cursor.stringValue(3);
                episode.messageId = cursor.intValue(4);
                episode.chatId = cursor.longValue(5);
                episode.accessHash = cursor.longValue(6);
                if (!cursor.isNull(7)) {
                    episode.fileReference = cursor.byteArrayValue(7);
                }
                episode.duration = cursor.intValue(8);
                episode.thumbnailPath = cursor.stringValue(9);
                episode.watchedPosition = cursor.longValue(10);
                episode.watchedComplete = cursor.intValue(11) == 1;
                episode.addedAt = cursor.longValue(12);
                episodes.add(episode);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return episodes;
    }
    
    public Episode getEpisode(long episodeId) {
        Episode episode = null;
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT id, season_id, episode_number, title, message_id, chat_id, access_hash, file_reference, duration, thumbnail_path, watched_position, watched_complete, added_at FROM episodes WHERE id = ?", episodeId);
            if (cursor.next()) {
                episode = new Episode();
                episode.id = cursor.longValue(0);
                episode.seasonId = cursor.longValue(1);
                episode.episodeNumber = cursor.intValue(2);
                episode.title = cursor.stringValue(3);
                episode.messageId = cursor.intValue(4);
                episode.chatId = cursor.longValue(5);
                episode.accessHash = cursor.longValue(6);
                if (!cursor.isNull(7)) {
                    episode.fileReference = cursor.byteArrayValue(7);
                }
                episode.duration = cursor.intValue(8);
                episode.thumbnailPath = cursor.stringValue(9);
                episode.watchedPosition = cursor.longValue(10);
                episode.watchedComplete = cursor.intValue(11) == 1;
                episode.addedAt = cursor.longValue(12);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return episode;
    }
    
    public boolean updateEpisodeWatchPosition(long episodeId, long position, boolean complete) {
        try {
            SQLitePreparedStatement state = database.executeFast("UPDATE episodes SET watched_position = ?, watched_complete = ? WHERE id = ?");
            state.requery();
            state.bindLong(1, position);
            state.bindInteger(2, complete ? 1 : 0);
            state.bindLong(3, episodeId);
            state.step();
            state.dispose();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
    
    public long addMovie(long collectionId, int messageId, long chatId, long accessHash, byte[] fileReference, int duration) {
        long id = -1;
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            SQLitePreparedStatement state = database.executeFast("INSERT INTO movies (collection_id, message_id, chat_id, access_hash, file_reference, duration, added_at) VALUES (?, ?, ?, ?, ?, ?, ?)");
            state.requery();
            state.bindLong(1, collectionId);
            state.bindInteger(2, messageId);
            state.bindLong(3, chatId);
            state.bindLong(4, accessHash);
            if (fileReference != null) {
                state.bindByteBuffer(5, fileReference);
            } else {
                state.bindNull(5);
            }
            state.bindInteger(6, duration);
            state.bindLong(7, currentTime);
            state.step();
            
            SQLiteCursor cursor = database.queryFinalized("SELECT last_insert_rowid()");
            if (cursor.next()) {
                id = cursor.longValue(0);
            }
            cursor.dispose();
            state.dispose();
            
            updateCollectionTimestamp(collectionId);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return id;
    }
    
    public Movie getMovie(long collectionId) {
        Movie movie = null;
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT id, collection_id, message_id, chat_id, access_hash, file_reference, duration, thumbnail_path, watched_position, watched_complete, added_at FROM movies WHERE collection_id = ?", collectionId);
            if (cursor.next()) {
                movie = new Movie();
                movie.id = cursor.longValue(0);
                movie.collectionId = cursor.longValue(1);
                movie.messageId = cursor.intValue(2);
                movie.chatId = cursor.longValue(3);
                movie.accessHash = cursor.longValue(4);
                if (!cursor.isNull(5)) {
                    movie.fileReference = cursor.byteArrayValue(5);
                }
                movie.duration = cursor.intValue(6);
                movie.thumbnailPath = cursor.stringValue(7);
                movie.watchedPosition = cursor.longValue(8);
                movie.watchedComplete = cursor.intValue(9) == 1;
                movie.addedAt = cursor.longValue(10);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return movie;
    }
    
    public boolean updateMovieWatchPosition(long movieId, long position, boolean complete) {
        try {
            SQLitePreparedStatement state = database.executeFast("UPDATE movies SET watched_position = ?, watched_complete = ? WHERE id = ?");
            state.requery();
            state.bindLong(1, position);
            state.bindInteger(2, complete ? 1 : 0);
            state.bindLong(3, movieId);
            state.step();
            state.dispose();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
    
    public List<Episode> getContinueWatchingEpisodes(int limit) {
        List<Episode> episodes = new ArrayList<>();
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT id, season_id, episode_number, title, message_id, chat_id, access_hash, file_reference, duration, thumbnail_path, watched_position, watched_complete, added_at FROM episodes WHERE watched_position > 0 AND watched_complete = 0 ORDER BY added_at DESC LIMIT ?", limit);
            while (cursor.next()) {
                Episode episode = new Episode();
                episode.id = cursor.longValue(0);
                episode.seasonId = cursor.longValue(1);
                episode.episodeNumber = cursor.intValue(2);
                episode.title = cursor.stringValue(3);
                episode.messageId = cursor.intValue(4);
                episode.chatId = cursor.longValue(5);
                episode.accessHash = cursor.longValue(6);
                if (!cursor.isNull(7)) {
                    episode.fileReference = cursor.byteArrayValue(7);
                }
                episode.duration = cursor.intValue(8);
                episode.thumbnailPath = cursor.stringValue(9);
                episode.watchedPosition = cursor.longValue(10);
                episode.watchedComplete = cursor.intValue(11) == 1;
                episode.addedAt = cursor.longValue(12);
                episodes.add(episode);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return episodes;
    }
    
    private void updateCollectionTimestamp(long collectionId) {
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            SQLitePreparedStatement state = database.executeFast("UPDATE media_collections SET updated_at = ? WHERE id = ?");
            state.requery();
            state.bindLong(1, currentTime);
            state.bindLong(2, collectionId);
            state.step();
            state.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
    
    private long getCollectionIdFromSeason(long seasonId) {
        long collectionId = -1;
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT collection_id FROM seasons WHERE id = ?", seasonId);
            if (cursor.next()) {
                collectionId = cursor.longValue(0);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return collectionId;
    }
}
