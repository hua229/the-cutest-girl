package com.thecutestgirl.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.thecutestgirl.app.R

/** 生成的业务图标保持原材质色；通用操作符号遵循当前主题的内容色，来源见素材 manifest。 */
@Composable
fun ArtworkIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val artwork = when (imageVector.name.substringAfterLast('.')) {
        "Add" -> R.drawable.art_add
        "Checkroom" -> R.drawable.art_outfit
        "Restaurant" -> R.drawable.art_meal
        "Favorite", "FavoriteBorder" -> R.drawable.art_event
        "WbTwilight" -> R.drawable.art_breakfast
        "WbSunny", "LightMode" -> R.drawable.art_lunch
        "DarkMode" -> R.drawable.art_dinner
        "Settings" -> R.drawable.art_settings
        "PersonOutline", "Person" -> R.drawable.art_profile
        "CloudSync" -> R.drawable.art_sync
        "Save" -> R.drawable.art_backup
        "BatterySaver" -> R.drawable.art_battery
        "Contrast" -> R.drawable.art_appearance
        else -> null
    }
    if (artwork != null) {
        Image(painterResource(artwork), contentDescription,
            modifier = modifier.then(Modifier.size(24.dp)), contentScale = ContentScale.Fit)
    } else {
        Icon(imageVector, contentDescription, modifier, tint)
    }
}
