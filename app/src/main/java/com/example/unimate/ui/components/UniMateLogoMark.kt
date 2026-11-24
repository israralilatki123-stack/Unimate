package com.example.unimate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.ui.theme.UniMateIndigoDeep

@Composable
fun UniMateLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    cornerRadius: Dp = (size.value * 0.24f).dp,
    borderColor: Color = Color.White.copy(alpha = 0.28f),
    shadowElevation: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = UniMateIndigoDeep,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_unimate_logo),
                contentDescription = stringResource(R.string.cd_unimate_logo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )
        }
    }
}
