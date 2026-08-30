package com.pip.wear.tile

import androidx.wear.tiles.ActionBuilders
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

        val root = LayoutElementBuilders.Column.Builder()
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(R.string.tile_label))
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build()
            )
            .build()

        val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(
                LayoutElementBuilders.Layout.Builder().setRoot(root).build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
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