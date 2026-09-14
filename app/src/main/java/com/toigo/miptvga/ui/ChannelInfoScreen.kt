package com.toigo.miptvga.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toigo.miptvga.MainViewModel
import com.toigo.miptvga.UiState
import com.toigo.miptvga.currentProgramForChannel
import com.toigo.miptvga.favoriteIdForChannel
import com.toigo.miptvga.groupIdForChannel
import com.toigo.miptvga.isChannelFavorite
import com.toigo.miptvga.nextProgramForChannel
import com.toigo.miptvga.ui.components.AppActionButton
import com.toigo.miptvga.ui.components.AppBackgroundColor
import com.toigo.miptvga.ui.components.AppPanel
import com.toigo.miptvga.ui.components.ChannelLogo
import com.toigo.miptvga.ui.components.MutedTextColor
import com.toigo.miptvga.ui.components.SecondaryTextColor
import com.toigo.miptvga.ui.components.SectionTitle
import com.toigo.miptvga.ui.components.StatusChip
import com.toigo.miptvga.ui.components.formatClock
import com.toigo.miptvga.ui.components.rememberDeviceTimeFormatter

@Composable
internal fun ChannelInfoScreen(
    ui: UiState,
    vm: MainViewModel
) {
    val channel = ui.channels.getOrNull(ui.selectedIndex)
    val currentProgram = channel?.let { currentProgramForChannel(it, ui.currentPrograms) }
    val nextProgram = channel?.let { nextProgramForChannel(it, ui.nextPrograms) }
    val timeFormatter = rememberDeviceTimeFormatter()
    val channelFavoriteId = channel?.let(::favoriteIdForChannel)
    val channelGroupId = channel?.let(::groupIdForChannel)
    val channelIsIndividuallyFavorite = channelFavoriteId?.let { it in ui.favoriteIds } == true
    val channelGroupIsFavorite = channelGroupId?.let { it in ui.favoriteGroupIds } == true
    val channelIsFavorite = channel?.let { isChannelFavorite(it, ui.favoriteIds, ui.favoriteGroupIds) } == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(20.dp)
    ) {
        AppPanel(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (channel == null) {
                SectionTitle(
                    title = "Información del canal",
                    subtitle = "Selecciona un canal desde la pantalla principal"
                )
                AppActionButton(text = "Volver", onClick = vm::openMain, modifier = Modifier.width(180.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChannelLogo(
                        showChannelLogos = true,
                        logoUrl = channel.logoUrl,
                        channelName = channel.name,
                        modifier = Modifier
                            .width(120.dp)
                            .height(72.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = channel.group?.ifBlank { "Sin grupo" } ?: "Sin grupo",
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(text = if (channelIsFavorite) "Favorito" else "Normal")
                            if (channelIsIndividuallyFavorite) {
                                StatusChip(text = "Canal favorito")
                            }
                            if (channelGroupIsFavorite) {
                                StatusChip(text = "Grupo favorito")
                            }
                            StatusChip(text = ui.epgStatus, accent = ui.epgSettings.enabled)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppActionButton(
                        text = "Volver",
                        onClick = vm::openMain,
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = "Guía",
                        onClick = vm::openGuide,
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = if (channelIsIndividuallyFavorite) "Canal ★" else "Canal ☆",
                        onClick = { vm.toggleFavorite(ui.selectedIndex) },
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = if (channelGroupIsFavorite) "Grupo ★" else "Grupo ☆",
                        onClick = vm::toggleFavoriteGroupForSelectedChannel,
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = "Reproducir",
                        onClick = vm::openMain,
                        modifier = Modifier.weight(1f),
                        primary = true
                    )
                }

                AppPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionTitle(title = "Emisión actual")
                    Text(
                        text = currentProgram?.title ?: "No hay información EPG disponible",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    currentProgram?.let { program ->
                        Text(
                            text = "${timeFormatter.formatClock(program.startTimeMillis)} - ${timeFormatter.formatClock(program.endTimeMillis)}",
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = nextProgram?.let { "Siguiente: ${it.title} · ${timeFormatter.formatClock(it.startTimeMillis)}" }
                            ?: "No hay siguiente programa disponible",
                        color = MutedTextColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                AppPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionTitle(title = "Datos del canal")
                    Text(text = "Grupo: ${channel.group?.ifBlank { "Sin grupo" } ?: "Sin grupo"}", color = Color.White)
                    Text(text = "TVG-ID: ${channel.tvgId?.ifBlank { "No disponible" } ?: "No disponible"}", color = Color.White)
                    Text(text = "Stream: ${channel.streamUrl}", color = SecondaryTextColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
