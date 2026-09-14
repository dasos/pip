package com.pip.wear.tile

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.pip.wear.R
import com.pip.wear.ui.recording.RecordingActivity

private const val ICON_ID = "pip_icon"
private const val RESOURCES_VERSION = "1"

/**
 * Static "tap to open" launcher tile. Tapping it opens the full-screen
 * recording activity where the press-and-hold capture happens.
 */
class RecordTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val launchAction: ActionBuilders.Action = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(RecordingActivity::class.java.name)
                    .build()
            )
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_recording")
            .setOnClick(launchAction)
            .build()

        val icon = LayoutElementBuilders.Image.Builder()
            .setResourceId(ICON_ID)
            .setWidth(DimensionBuilders.DpProp.Builder().setValue(48f).build())
            .setHeight(DimensionBuilders.DpProp.Builder().setValue(48f).build())
            .setContentDescription(getString(R.string.tile_label))
            .build()

        val label = LayoutElementBuilders.Text.Builder()
            .setText(getString(R.string.tile_label))
            .setColor(
                ColorBuilders.ColorProp.Builder()
                    .setArgb(getColor(R.color.tile_text_color))
                    .build()
            )
            .build()

        val root = LayoutElementBuilders.Column.Builder()
            .addContent(icon)
            .addContent(label)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build()
            )
            .build()

        val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(
                LayoutElementBuilders.Layout.Builder().setRoot(root).build()
            )
            .build()

        val resources = TileBuilders.Resources.Builder()
            .addId(ICON_ID, R.drawable.tile_preview)
            .setVersion(RESOURCES_VERSION)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setResources(resources)
            .setTimeline(
                TimelineBuilders.Timeline.Builder().addTimelineEntry(timelineEntry).build()
            )
            .build()

        return CallbackToFutureAdapter.getFuture { completer ->
            completer.set(tile)
            "open_recording"
        }
    }
}