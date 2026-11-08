# Application flow and state

1. Android launches `MainApplication.onCreate`, which constructs `DatabaseInitializer` (`data.db`), and `MainActivity.onCreate`, which restores a `PersistMap` from the last non-configuration instance and calls `setContent`. Coil's application image loader uses `cacheDir/coil`.
2. `MainActivity.onStart` binds `PlayerService` with `BIND_AUTO_CREATE`; the binder is held as Compose mutable state and exposed through `LocalPlayerServiceBinder`. `onStop` unbinds; playback can continue as a foreground service. `PlayerService.onCreate` creates player/cache, optionally restores queue, creates session and notification channel.
3. `HomeScreen` offers home sections, search and settings. Global album, artist and remote playlist routes are defined in `ui/screens/Routes.kt`; the custom `compose-routing` handlers dispatch composables. Local playlists, search results and settings have their own routes. This is not Jetpack Navigation.
4. Search requests in `SearchResultScreen` call `Innertube.searchPage` with a filter and continuation, map renderer data to item models, then render. Selecting a song calls `binder.player.forcePlay(song.asMediaItem)` and `binder.setupRadio(...)`; see [playback](PLAYBACK_ENGINE.md). UI can also select album/artist/playlist routes or interact with local history/favorites.
5. `MainActivity.onNewIntent` maps YouTube playlist, watch, channel/c and youtu.be URLs to routes or song lookup/playback. It is exported with browsable intent filters. Not every YouTube URL shape is handled.

```mermaid
sequenceDiagram
    actor User
    participant UI as Compose screen
    participant Net as Innertube
    participant S as PlayerService
    participant DB as Room
    User->>UI: Search
    UI->>Net: searchPage(query, filter)
    Net-->>UI: Result<ItemsPage> + continuation
    User->>UI: Select song
    UI->>S: forcePlay(MediaItem); setupRadio(endpoint)
    S->>Net: player(videoId), next(...)
    S->>DB: insert song / format / play events
    S-->>UI: Player listener state
```

State is mostly composable `remember`, `rememberSaveable`, `persist` tags and Room `Flow` collected in screens. `compose-persist` is a retained in-memory map, not Room/DataStore and not a guarantee of process-death persistence. `utils/Preferences.kt` adapts private SharedPreferences into mutable Compose state for appearance, playback, sort order and other settings. The service listens for preference changes. Network results and errors are handled in individual screens; there is no centralized state store or dependency injection framework. UI actions frequently call the DAO, client or player directly. See [architecture](ARCHITECTURE.md) and [database](DATABASE.md).
