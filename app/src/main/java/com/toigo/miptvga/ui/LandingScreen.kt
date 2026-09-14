package com.toigo.miptvga.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toigo.miptvga.R
import com.toigo.miptvga.UiState
import com.toigo.miptvga.ui.components.AppActionButton
import com.toigo.miptvga.ui.components.AppBackgroundColor
import com.toigo.miptvga.ui.components.AppByline
import com.toigo.miptvga.ui.components.AppFullName
import com.toigo.miptvga.ui.components.AppLogoDescription
import com.toigo.miptvga.ui.components.AppPanel
import com.toigo.miptvga.ui.components.ItemShape
import com.toigo.miptvga.ui.components.MutedTextColor
import com.toigo.miptvga.ui.components.SecondaryTextColor
import com.toigo.miptvga.ui.components.SectionTitle
import com.toigo.miptvga.ui.components.StatusChip
import com.toigo.miptvga.ui.components.appTextFieldColors
import com.toigo.miptvga.ui.components.sourceDisplayLabel
import com.toigo.miptvga.ui.components.sourceTypeLabel

@Composable
internal fun LandingScreen(
    ui: UiState,
    url: String,
    onUrlChange: (String) -> Unit,
    onLoadUrl: () -> Unit,
    onLoadFile: () -> Unit,
    onReloadLast: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 640.dp
        val logoSize = when {
            maxHeight < 520.dp -> 72.dp
            compact -> 112.dp
            else -> 148.dp
        }
        val panelSpacing = if (compact) 8.dp else 12.dp
        val panelPadding = if (compact) {
            PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        } else {
            PaddingValues(horizontal = 24.dp, vertical = 18.dp)
        }

        AppPanel(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 820.dp)
                .heightIn(max = maxHeight)
                .verticalScroll(scrollState),
            contentPadding = panelPadding,
            verticalArrangement = Arrangement.spacedBy(panelSpacing)
        ) {
            StatusChip(text = "Android TV · Caja Android", accent = true)
            Image(
                painter = painterResource(id = R.drawable.miptvga),
                contentDescription = AppLogoDescription,
                modifier = Modifier
                    .size(logoSize)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = AppFullName,
                color = Color.White,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            if (!compact) {
                Text(
                    text = "Interfaz simple, rápida y pensada para reproducir tus listas M3U con el mínimo consumo posible.",
                    color = MutedTextColor,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                text = AppByline,
                color = MutedTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("URL M3U") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !ui.isLoading,
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
                    text = "Cargar URL",
                    onClick = onLoadUrl,
                    enabled = !ui.isLoading,
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Cargar archivo",
                    onClick = onLoadFile,
                    enabled = !ui.isLoading,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Última lista",
                    onClick = onReloadLast,
                    enabled = !ui.isLoading && ui.playlistSource != null,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppActionButton(
                    text = "Ajustes",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "About",
                    onClick = onOpenAbout,
                    modifier = Modifier.weight(1f)
                )
            }
            AppPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionTitle(
                    title = "Estado",
                    subtitle = if (ui.isLoading) "Procesando lista..." else ui.status
                )
                StatusChip(text = "Origen: ${ui.playlistSource.sourceTypeLabel()}")
                Text(
                    text = ui.playlistSource.sourceDisplayLabel(),
                    color = SecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
                StatusChip(
                    text = if (ui.channels.isEmpty()) "Aún no hay canales cargados" else "${ui.channels.size} canales disponibles"
                )
            }
        }
    }
}

@Composable
internal fun WelcomePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            StatusChip(text = "Listo para reproducir", accent = true)
            Image(
                painter = painterResource(id = R.drawable.miptvga),
                contentDescription = AppLogoDescription,
                modifier = Modifier.size(152.dp)
            )
            Text(
                text = AppFullName,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Carga una lista M3U por URL o archivo local y empieza a reproducir al instante.",
                color = MutedTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = AppByline,
                color = MutedTextColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
