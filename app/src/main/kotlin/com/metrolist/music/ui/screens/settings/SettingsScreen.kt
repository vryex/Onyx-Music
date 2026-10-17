/**
 * Sonet customization of the open-source Metrolist project.
 * Original copyright and GPL notices remain with the repository.
 */
package com.metrolist.music.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.ui.theme.importLyricsFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, latestVersionName: String) {
    // Preserve previously configured miscellaneous/appearance settings.
    // Always-on content features are restored even for upgraded installations
    // that had disabled them before those toggles were removed.
    val (description, setDescription) = rememberPreference(ShowArtistDescriptionKey, true)
    val (subscribers, setSubscribers) = rememberPreference(ShowArtistSubscriberCountKey, true)
    val (listeners, setListeners) = rememberPreference(ShowMonthlyListenersKey, true)
    val (lrc, setLrc) = rememberPreference(EnableLrcLibKey, true)
    val (kugou, setKugou) = rememberPreference(EnableKugouKey, true)
    val (better, setBetter) = rememberPreference(EnableBetterLyricsKey, true)
    val (paxsenix, setPaxsenix) = rememberPreference(EnablePaxsenixKey, true)
    val (plus, setPlus) = rememberPreference(EnableLyricsPlus, true)
    val (zemer, setZemer) = rememberPreference(EnableZemerKey, true)
    val (popular, setPopular) = rememberPreference(ShowMostStatsPlaylistsKey, true)
    val (wrapped, setWrapped) = rememberPreference(ShowWrappedCardKey, false)
    val (proxy, setProxy) = rememberPreference(ProxyEnabledKey, false)
    val (explicit, setExplicit) = rememberPreference(HideExplicitKey, false)
    val (video, setVideo) = rememberPreference(HideVideoSongsKey, false)
    val (shorts, setShorts) = rememberPreference(HideYoutubeShortsKey, false)
    val (sonetLyricsFont, setSonetLyricsFont) = rememberPreference(SonetLyricsFontKey, "Outfit")
    val (customFontName, setCustomFontName) = rememberPreference(SonetLyricsFontLabelKey, "")
    var showFontPicker by rememberSaveable { mutableStateOf(false) }
    val fontContext = LocalContext.current
    val fontScope = rememberCoroutineScope()
    val importFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pickedUri ->
        if (pickedUri != null) {
            fontScope.launch {
                try {
                    val imported = importLyricsFont(fontContext, pickedUri)
                    setCustomFontName(imported.displayName)
                    setSonetLyricsFont(imported.preferenceValue)
                    showFontPicker = false
                    Toast.makeText(fontContext, fontContext.getString(R.string.sonet_font_import_success), Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(
                        fontContext,
                        fontContext.getString(R.string.sonet_font_import_error),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(
        description, subscribers, listeners,
        lrc, kugou, better, paxsenix, plus, zemer,
        popular, wrapped, proxy, explicit, video, shorts
    ) {
        if (!description) setDescription(true)
        if (!subscribers) setSubscribers(true)
        if (!listeners) setListeners(true)
        if (!lrc) setLrc(true)
        if (!kugou) setKugou(true)
        if (!better) setBetter(true)
        if (!paxsenix) setPaxsenix(true)
        if (!plus) setPlus(true)
        if (!zemer) setZemer(true)
        if (!popular) setPopular(true)
        if (wrapped) setWrapped(false)
        if (proxy) setProxy(false)
        if (explicit) setExplicit(false)
        if (video) setVideo(false)
        if (shorts) setShorts(false)
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )
        com.metrolist.music.ui.theme.OnyxPageHeader(
            stringResource(R.string.settings), subtitle = stringResource(R.string.app_name)
        )
        Spacer(Modifier.height(16.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_player_content),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text(stringResource(R.string.player_and_audio)) },
                    onClick = { navController.navigate("settings/player") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.content)) },
                    onClick = { navController.navigate("settings/content") }
                )
            )
        )
        Spacer(Modifier.height(20.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.lyrics),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.sonet_font_title)) },
                    description = { androidx.compose.foundation.layout.Column {
                        Text(stringResource(R.string.sonet_font_description), style = MaterialTheme.typography.bodySmall)
                        Text(when {
                            sonetLyricsFont.startsWith("Custom:") ->
                                customFontName.ifBlank { stringResource(R.string.sonet_font_import) }
                            sonetLyricsFont == "Sans" -> stringResource(R.string.sonet_font_sans)
                            sonetLyricsFont == "Serif" -> stringResource(R.string.sonet_font_serif)
                            sonetLyricsFont == "Mono" -> stringResource(R.string.sonet_font_mono)
                            else -> "Outfit"
                        })
                    } },
                    onClick = { showFontPicker = true }
                )
            )
        )
        Spacer(Modifier.height(20.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_privacy),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.security),
                    title = { Text(stringResource(R.string.privacy)) },
                    onClick = { navController.navigate("settings/privacy") }
                )
            )
        )
        Spacer(Modifier.height(20.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_storage),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text(stringResource(R.string.storage)) },
                    onClick = { navController.navigate("settings/storage") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.restore),
                    title = { Text(stringResource(R.string.backup_restore)) },
                    onClick = { navController.navigate("settings/backup_restore") }
                )
            )
        )
        Spacer(Modifier.height(20.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_system),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.about)) },
                    onClick = { navController.navigate("settings/about") }
                )
            )
        )
        Spacer(Modifier.height(24.dp))
    }
    if (showFontPicker) {
        AlertDialog(
            onDismissRequest = { showFontPicker = false },
            title = { Text(stringResource(R.string.sonet_font_title)) },
            text = {
                Column {
                    listOf(
                        "Outfit" to "Outfit",
                        "Sans" to stringResource(R.string.sonet_font_sans),
                        "Serif" to stringResource(R.string.sonet_font_serif),
                        "Mono" to stringResource(R.string.sonet_font_mono)
                    ).forEach { (key, label) ->
                        TextButton(
                            onClick = {
                                setSonetLyricsFont(key)
                                showFontPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (sonetLyricsFont == key) "✓  $label" else label,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (sonetLyricsFont == key) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    TextButton(
                        onClick = { importFontLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (sonetLyricsFont.startsWith("Custom:"))
                                "✓  " + customFontName.ifBlank { stringResource(R.string.sonet_font_import) }
                            else stringResource(R.string.sonet_font_import),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.sonet_font_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        }
    )
}
