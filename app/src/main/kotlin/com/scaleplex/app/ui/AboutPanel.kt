package com.scaleplex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scaleplex.core.i18n.tr

/** About panel: version and copyright. */
@Composable
fun AboutPanel() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ScalePlex", fontSize = 32.sp)
        Text("${tr("about.version")} 0.1.0", fontSize = 14.sp)
        Text(tr("about.copyright"), fontSize = 12.sp)
        Text(
            "Multi-brand barcode scale manager · DIGI / DAHUA / YouSheng / HuiHong / Toledo / CAS",
            fontSize = 11.sp,
        )
    }
}
