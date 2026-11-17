package com.karim.aiprojectmanager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Static reference-derived background and unchanged logo; only the loading indicator animates. */
@Composable
internal fun StartupSplash(loading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF01091C))) {
        Image(painterResource(R.drawable.splash_reference_background), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val logoSize = (maxWidth * 0.70f).coerceAtMost(314.dp)
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
            modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.495f - logoSize / 2).size(logoSize),
            contentScale = ContentScale.Fit)
        if (loading || failed) {
            Column(Modifier.align(Alignment.TopCenter).offset(y = maxHeight * 0.67f)
                .fillMaxWidth(0.90f).heightIn(max = maxHeight * 0.19f)
                .verticalScroll(rememberScrollState())
                .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failed) {
                    Text(stringResource(R.string.startup_failed), color = Color.White,
                        textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.startup_retry), color = Color.White)
                    }
                } else {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
                    Text(stringResource(R.string.startup_loading), color = Color.White,
                        textAlign = TextAlign.Center)
                }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.855f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AI Project Manager",
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
            Text(
                "© Karim S.K. Morsi 2026",
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
