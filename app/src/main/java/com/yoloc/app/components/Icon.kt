package com.yoloc.app.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yoloc.app.R
import com.yoloc.app.theme.StatusKey
import com.yoloc.app.theme.YoloCTheme

/**
 * RN: IconName (Feather). The icons the app uses, as vector drawables converted from the Feather
 * SVGs (MIT; licence in assets/licenses). `key` is Feather's name, which strings and the learn
 * topics refer to.
 */
enum class IconName(val key: String, @param:DrawableRes val drawable: Int) {
    AlertCircle("alert-circle", R.drawable.ic_alert_circle),
    AlertOctagon("alert-octagon", R.drawable.ic_alert_octagon),
    AlertTriangle("alert-triangle", R.drawable.ic_alert_triangle),
    ArrowLeft("arrow-left", R.drawable.ic_arrow_left),
    ArrowRight("arrow-right", R.drawable.ic_arrow_right),
    BarChart2("bar-chart-2", R.drawable.ic_bar_chart_2),
    BookOpen("book-open", R.drawable.ic_book_open),
    Camera("camera", R.drawable.ic_camera),
    CameraOff("camera-off", R.drawable.ic_camera_off),
    Check("check", R.drawable.ic_check),
    CheckCircle("check-circle", R.drawable.ic_check_circle),
    CheckSquare("check-square", R.drawable.ic_check_square),
    ChevronDown("chevron-down", R.drawable.ic_chevron_down),
    ChevronRight("chevron-right", R.drawable.ic_chevron_right),
    Circle("circle", R.drawable.ic_circle),
    Clock("clock", R.drawable.ic_clock),
    Cpu("cpu", R.drawable.ic_cpu),
    Crop("crop", R.drawable.ic_crop),
    Crosshair("crosshair", R.drawable.ic_crosshair),
    Database("database", R.drawable.ic_database),
    Disc("disc", R.drawable.ic_disc),
    Droplet("droplet", R.drawable.ic_droplet),
    Edit2("edit-2", R.drawable.ic_edit_2),
    Edit3("edit-3", R.drawable.ic_edit_3),
    Eye("eye", R.drawable.ic_eye),
    Flag("flag", R.drawable.ic_flag),
    Grid("grid", R.drawable.ic_grid),
    HardDrive("hard-drive", R.drawable.ic_hard_drive),
    Home("home", R.drawable.ic_home),
    Image("image", R.drawable.ic_image),
    Info("info", R.drawable.ic_info),
    Layers("layers", R.drawable.ic_layers),
    Lock("lock", R.drawable.ic_lock),
    Map("map", R.drawable.ic_map),
    MapPin("map-pin", R.drawable.ic_map_pin),
    Maximize("maximize", R.drawable.ic_maximize),
    Minus("minus", R.drawable.ic_minus),
    Moon("moon", R.drawable.ic_moon),
    Move("move", R.drawable.ic_move),
    Package("package", R.drawable.ic_package),
    PauseCircle("pause-circle", R.drawable.ic_pause_circle),
    Play("play", R.drawable.ic_play),
    Plus("plus", R.drawable.ic_plus),
    RefreshCw("refresh-cw", R.drawable.ic_refresh_cw),
    RotateCcw("rotate-ccw", R.drawable.ic_rotate_ccw),
    RotateCw("rotate-cw", R.drawable.ic_rotate_cw),
    Save("save", R.drawable.ic_save),
    ScanLine("scan-line", R.drawable.ic_scan_line),
    Search("search", R.drawable.ic_search),
    Settings("settings", R.drawable.ic_settings),
    Share2("share-2", R.drawable.ic_share_2),
    Shield("shield", R.drawable.ic_shield),
    Slash("slash", R.drawable.ic_slash),
    Sliders("sliders", R.drawable.ic_sliders),
    Smartphone("smartphone", R.drawable.ic_smartphone),
    Square("square", R.drawable.ic_square),
    Sun("sun", R.drawable.ic_sun),
    Tag("tag", R.drawable.ic_tag),
    Trash2("trash-2", R.drawable.ic_trash_2),
    Umbrella("umbrella", R.drawable.ic_umbrella),
    User("user", R.drawable.ic_user),
    WifiOff("wifi-off", R.drawable.ic_wifi_off),
    X("x", R.drawable.ic_x),
    Zap("zap", R.drawable.ic_zap),
    ZapOff("zap-off", R.drawable.ic_zap_off),
    ;

    companion object {
        /** For names that live in data (learn topics, photo tips); unknown ones show Info. */
        fun fromKey(key: String): IconName = entries.firstOrNull { it.key == key } ?: Info
    }
}

/** Status is never shown by color alone: each status has its own icon shape. */
val statusIcon: Map<StatusKey, IconName> = mapOf(
    StatusKey.Healthy to IconName.CheckCircle,
    StatusKey.Early to IconName.AlertTriangle,
    StatusKey.Infected to IconName.AlertOctagon,
)

/**
 * RN: components/Icon.tsx. Decorative: hidden from accessibility (the control or text next to it
 * carries the meaning), which is why there is no content description.
 */
@Composable
fun Icon(name: IconName, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = YoloCTheme.colors.icon) {
    Icon(
        painter = painterResource(name.drawable),
        contentDescription = null,
        modifier = modifier.then(Modifier.size(size)),
        tint = color,
    )
}
