package com.sandbox.sqlcipher.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sandbox.sqlcipher.R
import com.sandbox.sqlcipher.telephony.OwnPhoneNumber
import com.sandbox.sqlcipher.ui.Destination
import com.sandbox.sqlcipher.ui.events.EventsScreen
import com.sandbox.sqlcipher.ui.users.UsersScreen
import kotlinx.coroutines.launch

private val Destination.titleRes
    get() = when (this) {
        Destination.USERS -> R.string.screen_users
        Destination.EVENTS -> R.string.screen_events
    }

private val Destination.icon: ImageVector
    get() = when (this) {
        Destination.USERS -> Icons.Default.Person
        Destination.EVENTS -> Icons.AutoMirrored.Filled.List
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(destination: Destination, onNavigate: (Destination) -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ownNumber = remember { OwnPhoneNumber.read(context) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 28.dp, top = 24.dp, end = 28.dp),
                )
                Text(
                    ownNumber?.let { stringResource(R.string.own_number, it) }
                        ?: stringResource(R.string.own_number_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp, end = 28.dp, bottom = 16.dp),
                )
                HorizontalDivider()
                Destination.entries.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(item.titleRes)) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = item == destination,
                        onClick = {
                            onNavigate(item)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(destination.titleRes)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                )
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (destination) {
                Destination.USERS -> UsersScreen(modifier)
                Destination.EVENTS -> EventsScreen(modifier)
            }
        }
    }
}
