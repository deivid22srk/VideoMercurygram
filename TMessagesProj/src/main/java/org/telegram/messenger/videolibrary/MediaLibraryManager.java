package org.telegram.messenger.videolibrary;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.List;

public class MediaLibraryManager {
    
    private static volatile MediaLibraryManager[] Instance = new MediaLibraryManager[3];
    private static final Object[] lockObjects = new Object[3];
    
    static {
        for (int i = 0; i < 3; i++) {
            lockObjects[i] = new Object();
        }
    }
    
    private final int currentAccount;
    private MediaLibraryStorage storage;
    private boolean isInitialized = false;
    
    public static MediaLibraryManager getInstance(int num) {
        MediaLibraryManager localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new MediaLibraryManager(num);
                }
            }
        }
        return localInstance;
    }
    
    private MediaLibraryManager(int account) {
        this.currentAccount = account;
    }
    
    public void initialize() {
        if (isInitialized) {
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            try {
                storage = new MediaLibraryStorage(messagesStorage.getDatabase());
                storage.createTables();
                isInitialized = true;
                FileLog.d("MediaLibraryManager initialized for account " + currentAccount);
            } catch (Exception e) {
                FileLog.e("Failed to initialize MediaLibraryManager", e);
            }
        });
    }
    
    public void createCollection(String title, CollectionType type, String description, CreateCollectionCallback callback) {
        if (!isInitialized) {
            initialize();
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            long collectionId = storage.createCollection(title, type, description);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(collectionId);
                });
            }
        });
    }
    
    public void updateCollection(long collectionId, String title, String description, String posterPath, UpdateCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            boolean success = storage.updateCollection(collectionId, title, description, posterPath);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(success);
                });
            }
        });
    }
    
    public void deleteCollection(long collectionId, DeleteCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            boolean success = storage.deleteCollection(collectionId);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(success);
                });
            }
        });
    }
    
    public void getCollections(GetCollectionsCallback callback) {
        if (!isInitialized) {
            initialize();
            if (callback != null) {
                callback.onResult(null);
            }
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            List<MediaCollection> collections = storage.getCollections();
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(collections);
                });
            }
        });
    }
    
    public void getCollection(long collectionId, GetCollectionCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(null);
            }
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            MediaCollection collection = storage.getCollection(collectionId);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(collection);
                });
            }
        });
    }
    
    public void addSeason(long collectionId, int seasonNumber, String title, CreateSeasonCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(-1);
            }
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            long seasonId = storage.addSeason(collectionId, seasonNumber, title);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(seasonId);
                });
            }
        });
    }
    
    public void deleteSeason(long seasonId, DeleteCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            boolean success = storage.deleteSeason(seasonId);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(success);
                });
            }
        });
    }
    
    public void addEpisodeFromMessage(long seasonId, int episodeNumber, String title, MessageObject messageObject, CreateEpisodeCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(-1);
            }
            return;
        }
        
        if (messageObject == null || messageObject.messageOwner == null) {
            if (callback != null) {
                callback.onResult(-1);
            }
            return;
        }
        
        TLRPC.Message message = messageObject.messageOwner;
        int messageId = message.id;
        long chatId = messageObject.getDialogId();
        
        long accessHash = 0;
        byte[] fileReference = null;
        int duration = 0;
        
        if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            TLRPC.Document document = message.media.document;
            if (document != null) {
                accessHash = document.access_hash;
                fileReference = document.file_reference;
                
                for (TLRPC.DocumentAttribute attribute : document.attributes) {
                    if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                        duration = attribute.duration;
                        break;
                    }
                }
            }
        }
        
        final long finalAccessHash = accessHash;
        final byte[] finalFileReference = fileReference;
        final int finalDuration = duration;
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            long episodeId = storage.addEpisode(seasonId, episodeNumber, title, messageId, chatId, finalAccessHash, finalFileReference, finalDuration);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(episodeId);
                });
            }
        });
    }
    
    public void deleteEpisode(long episodeId, DeleteCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            boolean success = storage.deleteEpisode(episodeId);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(success);
                });
            }
        });
    }
    
    public void updateEpisodeWatchPosition(long episodeId, long position, boolean complete) {
        if (!isInitialized) {
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            storage.updateEpisodeWatchPosition(episodeId, position, complete);
        });
    }
    
    public void addMovieFromMessage(long collectionId, MessageObject messageObject, CreateMovieCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(-1);
            }
            return;
        }
        
        if (messageObject == null || messageObject.messageOwner == null) {
            if (callback != null) {
                callback.onResult(-1);
            }
            return;
        }
        
        TLRPC.Message message = messageObject.messageOwner;
        int messageId = message.id;
        long chatId = messageObject.getDialogId();
        
        long accessHash = 0;
        byte[] fileReference = null;
        int duration = 0;
        
        if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            TLRPC.Document document = message.media.document;
            if (document != null) {
                accessHash = document.access_hash;
                fileReference = document.file_reference;
                
                for (TLRPC.DocumentAttribute attribute : document.attributes) {
                    if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                        duration = attribute.duration;
                        break;
                    }
                }
            }
        }
        
        final long finalAccessHash = accessHash;
        final byte[] finalFileReference = fileReference;
        final int finalDuration = duration;
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            long movieId = storage.addMovie(collectionId, messageId, chatId, finalAccessHash, finalFileReference, finalDuration);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(movieId);
                });
            }
        });
    }
    
    public void updateMovieWatchPosition(long movieId, long position, boolean complete) {
        if (!isInitialized) {
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            storage.updateMovieWatchPosition(movieId, position, complete);
        });
    }
    
    public void getContinueWatchingEpisodes(int limit, GetEpisodesCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onResult(null);
            }
            return;
        }
        
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            List<Episode> episodes = storage.getContinueWatchingEpisodes(limit);
            if (callback != null) {
                Utilities.stageQueue.postRunnable(() -> {
                    callback.onResult(episodes);
                });
            }
        });
    }
    
    public interface CreateCollectionCallback {
        void onResult(long collectionId);
    }
    
    public interface CreateSeasonCallback {
        void onResult(long seasonId);
    }
    
    public interface CreateEpisodeCallback {
        void onResult(long episodeId);
    }
    
    public interface CreateMovieCallback {
        void onResult(long movieId);
    }
    
    public interface GetCollectionsCallback {
        void onResult(List<MediaCollection> collections);
    }
    
    public interface GetCollectionCallback {
        void onResult(MediaCollection collection);
    }
    
    public interface GetEpisodesCallback {
        void onResult(List<Episode> episodes);
    }
    
    public interface UpdateCallback {
        void onResult(boolean success);
    }
    
    public interface DeleteCallback {
        void onResult(boolean success);
    }
}
