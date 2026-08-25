/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.SnapLayoutInfoProvider
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.ChartsViewModel
import com.metrolist.music.viewmodels.ExploreViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    navController: NavController,
    embedded: Boolean = false,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val explorePage by exploreViewModel.explorePage.collectAsStateWithLifecycle()
    val chartsPage by chartsViewModel.chartsPage.collectAsStateWithLifecycle()
    val isChartsLoading by chartsViewModel.isLoading.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTopFallback = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val scrollToTop by (backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)
        ?: scrollToTopFallback).collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            chartsViewModel.loadCharts()
        }
    }

    LaunchedEffect(scrollToTop) {
        if (!embedded && scrollToTop) {
            scrollState.animateScrollTo(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Box(
        modifier = if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = if (embedded) Modifier.fillMaxWidth() else Modifier.verticalScroll(scrollState),
        ) {
            if (!embedded) {
                Spacer(
                    Modifier.height(
                        LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateTopPadding(),
                    ),
                )
            }

            if (isChartsLoading || chartsPage == null || explorePage == null) {
                ShimmerHost {
                    TextPlaceholder(
                        height = 36.dp,
                        modifier =
                            Modifier
                                .padding(12.dp)
                                .fillMaxWidth(0.5f),
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(4),
                            contentPadding = PaddingValues(start = 4.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(ListItemHeight * 4),
                        ) {
                            items(4) {
                                Row(
                                    modifier =
                                        Modifier
                                            .width(horizontalLazyGridItemWidth)
                                            .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(ListItemHeight - 16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.onSurface),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .height(16.dp)
                                                    .width(120.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier =
                                                Modifier
                                                    .height(12.dp)
                                                    .width(80.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextPlaceholder(
                        height = 36.dp,
                        modifier =
                            Modifier
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .width(250.dp),
                    )
                    Row {
                        repeat(2) {
                            GridItemPlaceHolder()
                        }
                    }

                    TextPlaceholder(
                        height = 36.dp,
                        modifier =
                            Modifier
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .width(250.dp),
                    )
                    Row {
                        repeat(2) {
                            GridItemPlaceHolder()
                        }
                    }

                    TextPlaceholder(
                        height = 36.dp,
                        modifier =
                            Modifier
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .width(250.dp),
                    )
                    repeat(4) {
                        Row {
                            repeat(2) {
                                TextPlaceholder(
                                    height = MoodAndGenresButtonHeight,
                                    modifier =
                                        Modifier
                                            .padding(horizontal = 6.dp)
                                            .width(200.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                chartsPage?.sections?.filter { it.title != "Top music videos" }?.forEach { section ->
                    NavigationTitle(
                        title =
                            when (section.title) {
                                "Trending" -> stringResource(R.string.trending)
                                else -> section.title.ifEmpty { stringResource(R.string.charts) }
                            },
                    )
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                        val lazyGridState = rememberLazyGridState()
                        val snapLayoutInfoProvider =
                            remember(lazyGridState) {
                                SnapLayoutInfoProvider(
                                    lazyGridState = lazyGridState,
                                    positionInLayout = { layoutSize, itemSize ->
                                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                                    },
                                )
                            }

                        LazyHorizontalGrid(
                            state = lazyGridState,
                            rows = GridCells.Fixed(4),
                            flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                            contentPadding =
                                WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal)
                                    .asPaddingValues(),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(ListItemHeight * 4),
                        ) {
                            items(
                                items = section.items.filterIsInstance<SongItem>().distinctBy { it.id },
                                key = { "explore_song_${it.id}" },
                            ) { song ->
                                YouTubeListItem(
                                    item = song,
                                    isActive = song.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    isSwipeable = false,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .width(horizontalLazyGridItemWidth)
                                            .combinedClickable(
                                                onClick = {
                                                    if (song.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                endpoint = WatchEndpoint(videoId = song.id),
                                                                preloadItem = song.toMediaMetadata(),
                                                            ),
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ),
                                )
                            }
                        }
                    }
                }

                explorePage?.newReleaseAlbums?.let { newReleaseAlbums ->
                    NavigationTitle(
                        title = stringResource(R.string.new_release_albums),
                        onClick = {
                            navController.navigate("new_release")
                        },
                    )
                    LazyRow(
                        contentPadding =
                            WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                    ) {
                        items(
                            items = newReleaseAlbums.distinctBy { it.id },
                            key = { "explore_album_${it.id}" },
                        ) { album ->
                            YouTubeGridItem(
                                item = album,
                                isActive = mediaMetadata?.album?.id == album.id,
                                isPlaying = isPlaying,
                                coroutineScope = coroutineScope,
                                modifier =
                                    Modifier
                                        .combinedClickable(
                                            onClick = {
                                                navController.navigate("album/${album.id}")
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeAlbumMenu(
                                                        albumItem = album,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }
                }

                chartsPage?.sections?.find { it.title == "Top music videos" }?.let { topVideosSection ->
                    NavigationTitle(
                        title = stringResource(R.string.top_music_videos),
                    )
                    LazyRow(
                        contentPadding =
                            WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                    ) {
                        items(
                            items = topVideosSection.items.filterIsInstance<SongItem>().distinctBy { it.id },
                            key = { "explore_video_${it.id}" },
                        ) { video ->
                            YouTubeGridItem(
                                item = video,
                                isActive = video.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                coroutineScope = coroutineScope,
                                modifier =
                                    Modifier
                                        .combinedClickable(
                                            onClick = {
                                                if (video.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            endpoint = WatchEndpoint(videoId = video.id),
                                                            preloadItem = video.toMediaMetadata(),
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = video,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }
                }

                explorePage?.moodAndGenres?.let { moodAndGenres ->
                    NavigationTitle(
                        title = stringResource(R.string.mood_and_genres),
                        onClick = {
                            navController.navigate("mood_and_genres")
                        },
                    )
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(4),
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier.height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp),
                    ) {
                        items(moodAndGenres) {
                            MoodAndGenresButton(
                                title = it.title,
                                onClick = {
                                    navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                                },
                                modifier =
                                    Modifier
                                        .padding(6.dp)
                                        .width(180.dp),
                            )
                        }
                    }
                }
            }

            explorePage?.sections
                ?.filter { section ->
                    val title = section.title.lowercase()
                    title != "new albums" &&
                        !title.contains("mood") &&
                        !title.contains("genre") &&
                        section.items.isNotEmpty()
                }
                ?.forEach { section ->
                    NavigationTitle(
                        title = section.title,
                        onClick = section.endpoint?.let { endpoint ->
                            endpoint.browseId?.let { browseId ->
                                {
                                    navController.navigate(
                                        "youtube_browse/$browseId?params=${endpoint.params.orEmpty()}",
                                    )
                                }
                            }
                        },
                    )
                    LazyRow(
                        contentPadding =
                            WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                    ) {
                        items(
                            items = section.items.distinctBy { it.id },
                            key = { "explore_section_${section.title}_${it.id}" },
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
                                isPlaying = isPlaying,
                                coroutineScope = coroutineScope,
                                modifier =
                                    Modifier.combinedClickable(
                                        onClick = {
                                            when (item) {
                                                is SongItem -> {
                                                    if (item.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            if (autoRadioQueue) {
                                                                YouTubeQueue(
                                                                    item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                                    item.toMediaMetadata(),
                                                                )
                                                            } else {
                                                                ListQueue(
                                                                    title = item.title,
                                                                    items = listOf(item.toMediaItem()),
                                                                )
                                                            },
                                                        )
                                                    }
                                                }
                                                is AlbumItem -> navController.navigate("album/${item.id}")
                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                is PodcastItem -> navController.navigate("online_podcast/${item.id}")
                                                is EpisodeItem -> {
                                                    if (item.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue.radio(item.toMediaMetadata()),
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                when (item) {
                                                    is SongItem -> YouTubeSongMenu(song = item, onDismiss = menuState::dismiss)
                                                    is AlbumItem -> YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss)
                                                    is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                                                    is PlaylistItem -> YouTubePlaylistMenu(
                                                        playlist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                    is PodcastItem -> YouTubePlaylistMenu(
                                                        playlist = item.asPlaylistItem(),
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                    is EpisodeItem -> YouTubeSongMenu(
                                                        song = item.asSongItem(),
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            }
                                        },
                                    ),
                            )
                        }
                    }
                }

            if (!embedded) {
                Spacer(
                    Modifier.height(
                        LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
                    ),
                )
            }
        }
    }
}
