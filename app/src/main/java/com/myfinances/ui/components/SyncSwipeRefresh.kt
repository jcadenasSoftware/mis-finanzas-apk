package com.myfinances.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.myfinances.ui.viewmodel.SyncViewModel

@Composable
fun SyncSwipeRefresh(
    modifier: Modifier = Modifier,
    syncViewModel: SyncViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val isSyncing by syncViewModel.isSyncing.collectAsState()
    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = isSyncing),
        onRefresh = { syncViewModel.syncAll(force = true) },
        modifier = modifier
    ) {
        content()
    }
}
