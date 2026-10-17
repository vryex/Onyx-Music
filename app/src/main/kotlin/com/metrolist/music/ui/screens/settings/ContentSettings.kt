/**
 * Onyx Music customization of the open-source Metrolist project.
 * Original copyright and GPL-3.0 notices remain in the repository.
 */

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AddToPlaylistPosition
import com.metrolist.music.constants.AddToPlaylistPositionKey
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.ContentLanguageKey
import com.metrolist.music.constants.CountryCodeToName
import com.metrolist.music.constants.LanguageCodeToName
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

/**
 * Only settings that remain useful to Onyx Music are exposed here.
 * Artist metadata, lyric providers and weekly/monthly playlists are enabled
 * by the settings entry migration without removing their internal features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(navController: NavController) {
    val context = LocalContext.current
    val (language, setLanguage) = rememberPreference(ContentLanguageKey, "system")
    val (country, setCountry) = rememberPreference(ContentCountryKey, "system")
    val (appLanguage, setAppLanguage) = rememberPreference(AppLanguageKey, SYSTEM_DEFAULT)
    val (position, setPosition) = rememberEnumPreference(
        AddToPlaylistPositionKey, AddToPlaylistPosition.BEGINNING
    )
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var showCountry by rememberSaveable { mutableStateOf(false) }
    var showAppLanguage by rememberSaveable { mutableStateOf(false) }
    var showPosition by rememberSaveable { mutableStateOf(false) }

    if (showLanguage) {
        EnumDialog(
            onDismiss = { showLanguage = false },
            onSelect = { setLanguage(it); showLanguage = false },
            title = stringResource(R.string.content_language),
            current = language,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = { LanguageCodeToName[it] ?: stringResource(R.string.system_default) },
        )
    }
    if (showCountry) {
        EnumDialog(
            onDismiss = { showCountry = false },
            onSelect = { setCountry(it); showCountry = false },
            title = stringResource(R.string.content_country),
            current = country,
            values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
            valueText = { CountryCodeToName[it] ?: stringResource(R.string.system_default) },
        )
    }
    if (showAppLanguage) {
        EnumDialog(
            onDismiss = { showAppLanguage = false },
            onSelect = { setAppLanguage(it); showAppLanguage = false },
            title = stringResource(R.string.app_language),
            current = appLanguage,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = { LanguageCodeToName[it] ?: stringResource(R.string.system_default) },
        )
    }
    if (showPosition) {
        EnumDialog(
            onDismiss = { showPosition = false },
            onSelect = { setPosition(it); showPosition = false },
            title = stringResource(R.string.add_to_playlist_position),
            current = position,
            values = AddToPlaylistPosition.entries,
            valueText = {
                when (it) {
                    AddToPlaylistPosition.BEGINNING -> stringResource(R.string.playlist_position_beginning)
                    AddToPlaylistPosition.END -> stringResource(R.string.playlist_position_end)
                }
            },
            valueDescription = {
                when (it) {
                    AddToPlaylistPosition.BEGINNING -> stringResource(R.string.playlist_position_beginning_desc)
                    AddToPlaylistPosition.END -> stringResource(R.string.playlist_position_end_desc)
                }
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Material3SettingsGroup(
            title = stringResource(R.string.general),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.content_language)) },
                    description = { Text(LanguageCodeToName[language] ?: stringResource(R.string.system_default)) },
                    onClick = { showLanguage = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.location_on),
                    title = { Text(stringResource(R.string.content_country)) },
                    description = { Text(CountryCodeToName[country] ?: stringResource(R.string.system_default)) },
                    onClick = { showCountry = true }
                )
            )
        )
        Spacer(Modifier.height(24.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.playlists),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.playlist_add),
                    title = { Text(stringResource(R.string.add_to_playlist_position)) },
                    description = {
                        Text(
                            stringResource(
                                if (position == AddToPlaylistPosition.BEGINNING)
                                    R.string.playlist_position_beginning
                                else R.string.playlist_position_end
                            )
                        )
                    },
                    onClick = { showPosition = true }
                )
            )
        )
        Spacer(Modifier.height(24.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.app_language),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.app_language)) },
                    description = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Text(LanguageCodeToName[appLanguage] ?: stringResource(R.string.system_default))
                        }
                    },
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APP_LOCALE_SETTINGS,
                                    "package:${context.packageName}".toUri()
                                )
                            )
                        } else {
                            showAppLanguage = true
                        }
                    }
                )
            )
        )
        Spacer(Modifier.height(24.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                androidx.compose.material3.Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
