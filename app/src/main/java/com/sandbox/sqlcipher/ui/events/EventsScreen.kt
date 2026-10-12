package com.sandbox.sqlcipher.ui.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sandbox.sqlcipher.R
import com.sandbox.sqlcipher.data.db.EventEntity
import com.sandbox.sqlcipher.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    repository: EventRepository,
) : ViewModel() {
    /** null — данные ещё загружаются. */
    val events: StateFlow<List<EventEntity>?> = repository.observeEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun EventsScreen(modifier: Modifier = Modifier, viewModel: EventsViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }

    Box(modifier.fillMaxSize()) {
        val list = events
        when {
            list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            list.isEmpty() -> Text(
                stringResource(R.string.events_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> LazyColumn {
                items(list, key = { it.id }) { event ->
                    val number = event.phone.ifEmpty { stringResource(R.string.events_no_number) }
                    ListItem(
                        overlineContent = { Text(formatter.format(Date(event.timestamp))) },
                        headlineContent = { Text(event.event) },
                        supportingContent = {
                            Text(event.name?.let { "$it · $number" } ?: number)
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
