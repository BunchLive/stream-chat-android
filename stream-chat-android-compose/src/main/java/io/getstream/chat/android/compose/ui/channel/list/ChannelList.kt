package io.getstream.chat.android.compose.ui.channel.list

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QuerySort
import io.getstream.chat.android.client.models.Channel
import io.getstream.chat.android.client.models.Filters
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.compose.R
import io.getstream.chat.android.compose.handlers.LoadMoreHandler
import io.getstream.chat.android.compose.state.channel.list.ChannelsState
import io.getstream.chat.android.compose.ui.common.EmptyView
import io.getstream.chat.android.compose.ui.common.LoadingFooter
import io.getstream.chat.android.compose.ui.common.LoadingView
import io.getstream.chat.android.compose.viewmodel.channel.ChannelListViewModel
import io.getstream.chat.android.compose.viewmodel.channel.ChannelViewModelFactory
import io.getstream.chat.android.offline.ChatDomain

/**
 * Default ChannelList component, that relies on the [ChannelListViewModel] to load the data and
 * show it on the UI.
 *
 * @param modifier Modifier for styling.
 * @param viewModel The ViewModel that loads all the data and connects it to the UI. We provide a
 * factory that builds the default ViewModel in case the user doesn't want to provide their own.
 * @param onLastItemReached Handler for pagination, when the user reaches the last item in the list.
 * @param onChannelClick Handler for a single item tap.
 * @param onChannelLongClick Handler for a long item tap.
 * @param loadingContent Composable that represents the loading content, when we're loading the initial data.
 * @param emptyContent Composable that represents the empty content if there are no channels.
 * @param emptySearchContent Composable that represents the empty content if there are no channels matching the search query.
 * @param itemContent UI lambda function that allows the user to completely customize the item UI.
 * It shows [DefaultChannelItem] if left unchanged, with the actions provided by [onChannelClick] and
 * [onChannelLongClick].
 */
@Composable
public fun ChannelList(
    modifier: Modifier = Modifier,
    viewModel: ChannelListViewModel = viewModel(
        factory =
        ChannelViewModelFactory(
            ChatClient.instance(),
            ChatDomain.instance(),
            QuerySort.desc("last_updated"),
            Filters.and(
                Filters.eq("type", "messaging"),
                Filters.`in`("members", listOf(ChatClient.instance().getCurrentUser()?.id ?: ""))
            )
        )
    ),
    onLastItemReached: () -> Unit = { viewModel.loadMore() },
    onChannelClick: (Channel) -> Unit = {},
    onChannelLongClick: (Channel) -> Unit = { viewModel.selectChannel(it) },
    loadingContent: @Composable () -> Unit = { LoadingView(modifier) },
    emptyContent: @Composable () -> Unit = { DefaultChannelListEmptyView(modifier) },
    emptySearchContent: @Composable (String) -> Unit = { searchQuery ->
        DefaultChannelSearchEmptyView(
            searchQuery = searchQuery,
            modifier = modifier
        )
    },
    itemContent: @Composable (Channel) -> Unit = { channel ->
        DefaultChannelItem(
            channel = channel,
            currentUser = viewModel.user.value,
            onChannelClick = onChannelClick,
            onChannelLongClick = onChannelLongClick
        )
    },
) {

    ChannelList(
        modifier = modifier,
        channelsState = viewModel.channelsState,
        currentUser = viewModel.user.value,
        onLastItemReached = onLastItemReached,
        onChannelClick = onChannelClick,
        onChannelLongClick = onChannelLongClick,
        loadingContent = loadingContent,
        emptyContent = emptyContent,
        emptySearchContent = emptySearchContent,
        itemContent = itemContent
    )
}

/**
 * Root Channel list component, that represents different UI, based on the current channel state.
 *
 * This is decoupled from ViewModels, so the user can provide manual and custom data handling,
 * as well as define a completely custom UI component for the channel item.
 *
 * If there is no state, no query active or the data is being loaded, we show the [LoadingView].
 *
 * If there are no results or we're offline, usually due to an error in the API or network, we show an [EmptyView].
 *
 * If there is data available and it is not empty, we show [Channels].
 *
 * @param modifier Modifier for styling.
 * @param currentUser The data of the current user, used various states.
 * @param channelsState Current state of the Channel list, represented by [ChannelsState].
 * @param onLastItemReached Handler for pagination, when the user reaches the end of the list.
 * @param onChannelClick Handler for a single item tap.
 * @param onChannelLongClick Handler for a long item tap.
 * @param loadingContent Composable that represents the loading content, when we're loading the initial data.
 * @param emptyContent Composable that represents the empty content if there are no channels.
 * @param emptySearchContent Composable that represents the empty content if there are no channels matching the search query.
 * @param itemContent UI lambda function that allows the user to completely customize the item UI.
 * It shows [DefaultChannelItem] if left unchanged, with the actions provided by [onChannelClick] and
 * [onChannelLongClick].
 */
@Composable
public fun ChannelList(
    channelsState: ChannelsState,
    currentUser: User?,
    modifier: Modifier = Modifier,
    onLastItemReached: () -> Unit = {},
    onChannelClick: (Channel) -> Unit = {},
    onChannelLongClick: (Channel) -> Unit = {},
    loadingContent: @Composable () -> Unit = { LoadingView(modifier) },
    emptyContent: @Composable () -> Unit = { DefaultChannelListEmptyView(modifier) },
    emptySearchContent: @Composable (String) -> Unit = { searchQuery ->
        DefaultChannelSearchEmptyView(
            searchQuery = searchQuery,
            modifier = modifier
        )
    },
    itemContent: @Composable (Channel) -> Unit = { channel ->
        DefaultChannelItem(
            channel = channel,
            currentUser = currentUser,
            onChannelClick = onChannelClick,
            onChannelLongClick = onChannelLongClick
        )
    },
) {
    val (isLoading, _, _, channels, searchQuery) = channelsState

    when {
        isLoading -> loadingContent()
        !isLoading && channels.isNotEmpty() -> Channels(
            modifier = modifier,
            channelsState = channelsState,
            onLastItemReached = onLastItemReached,
            itemContent = itemContent
        )
        searchQuery.isNotEmpty() -> emptySearchContent(searchQuery)
        else -> emptyContent()
    }
}

/**
 * Builds a list of [DefaultChannelItem] elements, based on [channelsState] and action handlers that it receives.
 *
 * @param channelsState Exposes if we're loading more items, reaches the end of the list and the
 * current list of channels to show.
 * @param onLastItemReached Handler for when the user reaches the end of the list.
 * @param itemContent Customizable UI component, that represents each item in the list.
 * @param modifier Modifier for styling.
 */
@Composable
public fun Channels(
    channelsState: ChannelsState,
    onLastItemReached: () -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Channel) -> Unit,
) {
    val (_, isLoadingMore, endOfChannels, channels) = channelsState
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(
            items = channels,
            key = Channel::cid
        ) { item ->
            itemContent(item)
        }

        if (isLoadingMore) {
            item {
                LoadingFooter(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (!endOfChannels && channels.isNotEmpty()) {
        LoadMoreHandler(listState) {
            onLastItemReached()
        }
    }
}

/**
 * The default empty placeholder for the case when there are no channels available to the user.
 *
 * @param modifier Modifier for styling.
 */
@Composable
internal fun DefaultChannelListEmptyView(modifier: Modifier = Modifier) {
    EmptyView(
        modifier = modifier,
        painter = painterResource(id = R.drawable.stream_compose_empty_channels),
        text = stringResource(R.string.stream_compose_channel_list_empty_channels),
    )
}

/**
 * The default empty placeholder for the case when channel search returns no results.
 *
 * @param searchQuery The search query that returned no results.
 * @param modifier Modifier for styling.
 */
@Composable
internal fun DefaultChannelSearchEmptyView(
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    EmptyView(
        modifier = modifier,
        painter = painterResource(id = R.drawable.stream_compose_empty_search_results),
        text = stringResource(R.string.stream_compose_channel_list_empty_search_results, searchQuery),
    )
}