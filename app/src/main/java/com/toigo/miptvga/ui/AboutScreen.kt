package com.toigo.miptvga.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.toigo.miptvga.R
import com.toigo.miptvga.ui.components.AboutRepositoryUrl
import com.toigo.miptvga.ui.components.AppActionButton
import com.toigo.miptvga.ui.components.AppBackgroundColor
import com.toigo.miptvga.ui.components.AppByline
import com.toigo.miptvga.ui.components.AppFullName
import com.toigo.miptvga.ui.components.AppLogoDescription
import com.toigo.miptvga.ui.components.AppPanel
import com.toigo.miptvga.ui.components.MutedTextColor
import com.toigo.miptvga.ui.components.SecondaryTextColor
import com.toigo.miptvga.ui.components.StatusChip

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AppPanel(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusChip(text = "Acerca de", accent = true)
            Image(
                painter = painterResource(id = R.drawable.miptvga),
                contentDescription = AppLogoDescription,
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = AppFullName,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Aplicación ligera para Android TV y cajas Android que carga listas M3U por URL o archivo local, organiza miles de canales en un panel lateral y reproduce el canal seleccionado en el área central.",
                color = MutedTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = AppByline,
                color = SecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppActionButton(
                    text = "Volver",
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Abrir repositorio",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, AboutRepositoryUrl.toUri()))
                    },
                    modifier = Modifier.weight(1f),
                    primary = true
                )
            }
        }
    }
}
