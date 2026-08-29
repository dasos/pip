package com.pip.wear.tile

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.pip.wear.R
import com.pip.wear.ui.recording.RecordingActivity

/**
 * Static "tap to open" launcher tile. Tapping it opens the full-screen
 * recording activity where the press-and-hold capture happens.
 */
class RecordTileService : TileService() {

    override suspend fun onTileRequest(requestParams: TileService.TileRequest): TileBuilders.Tile {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RecordingActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val buttonClick = ActionBuilders.LaunchAction.Builder().setAndroidActivity(launchIntent).build()

        val button = LayoutElementBuilders.Button.Builder()
            .setText(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(R.string.hold_label))
                    .build()
            )
            .setContentDescription(getString(R.string.hold_label))
            .setOnClick(ActionBuilders.Action.Builder().setLaunchAction(buttonClick).build())
            .build()

        val label = LayoutElementBuilders.Text.Builder()
            .setText(getString(R.string.tile_label))
            .build()

        val root = LayoutElementBuilders.Column.Builder()
            .addContent(label)
            .addContent(button)
            .build()

        val progress = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(
                LayoutElementBuilders.Layout.Builder().setRoot(root).build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(progress).build())
            .build()
    }
}