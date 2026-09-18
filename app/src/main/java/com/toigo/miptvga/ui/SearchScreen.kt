package com.toigo.miptvga.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toigo.miptvga.MainViewModel
import com.toigo.miptvga.UiState
import com.toigo.miptvga.ui.components.AppActionButton
import com.toigo.miptvga.ui.components.AppBackgroundColor
import com.toigo.miptvga.ui.components.AppPanel
import com.toigo.miptvga.ui.components.ItemShape
import com.toigo.miptvga.ui.components.SectionTitle
import com.toigo.miptvga.ui.components.StatusChip
import com.toigo.miptvga.ui.components.appTextFieldColors
import com.toigo.miptvga.ui.components.ChannelList

@Composable
internal fun SearchScreen(
    ui: UiState,
    vm: MainViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(20.dp)
    ) {
        AppPanel(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(
                    title = "Buscar canales",
                    subtitle = "Filtra por nombre o grupo y abre el canal directamente"
                )
                StatusChip(text = "${ui.filteredChannels.size} resultados", accent = true)
            }

            OutlinedTextField(
                value = ui.searchQuery,
                onValueChange = vm::updateSearchQuery,
                label = { Text("Canal o grupo") },
                singleLine = true,
                shape = ItemShape,
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

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
                    text = "Limpiar",
                    onClick = { vm.updateSearchQuery("") },
                    modifier = Modifier.weight(1f),
                    enabled = ui.searchQuery.isNotBlank()
                )
                AppActionButton(
                    text = "Guía",
                    onClick = vm::openGuide,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Ajustes",
                    onClick = vm::openSettings,
                    modifier = Modifier.weight(1f)
                )
            }

            ChannelList(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                filteredChannels = ui.filteredChannels,
                favoriteIds = ui.favoriteIds,
                favoriteGroupIds = ui.favoriteGroupIds,
                currentPrograms = ui.currentPrograms,
                showChannelLogos = ui.showChannelLogos,
                selectedIndex = ui.selectedIndex,
                selectedVisibleIndex = ui.selectedVisibleIndex,
                onToggleFavorite = vm::toggleFavorite,
                onSelectChannel = {
                    vm.selectChannel(it)
                    vm.openMain()
                }
            )
        }
    }
}
