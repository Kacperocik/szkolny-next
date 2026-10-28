/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-20
 */

package pl.szczodrzynski.edziennik.ui.dialogs

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.DialogBellSyncBinding
import pl.szczodrzynski.edziennik.ext.moveButtonsToTitleWhenSpaceLimited
import pl.szczodrzynski.edziennik.ext.resolveDrawable
import pl.szczodrzynski.edziennik.ext.startCoroutineTimer
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.utils.models.Time

class BellSyncDialog(
    activity: AppCompatActivity,
    private val bellTime: Time,
) : BindingDialog<DialogBellSyncBinding>(activity) {

    override fun getTitleRes() = R.string.bell_sync_title
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogBellSyncBinding.inflate(layoutInflater)

    override fun getNeutralButtonText() = R.string.cancel

    private val actualBellDiff: Pair<Time, Int>
        get() {
            val now = Time.getNow()
            val bellDiff = Time.diff(now, bellTime)
            val multiplier = if (bellTime > now) -1 else 1
            return Pair(bellDiff, multiplier)
        }

    override suspend fun onShow() {
        dialog.moveButtonsToTitleWhenSpaceLimited()
        b.bellSyncButton.setOnClickListener {
            val (bellDiff, multiplier) = actualBellDiff
            val bellDiffText = (if (multiplier == -1) '-' else '+') + bellDiff.stringHMS
            app.config.timetable.bellSyncDiff = bellDiff
            app.config.timetable.bellSyncMultiplier = multiplier

            SimpleDialog<Unit>(activity) {
                title(R.string.bell_sync_title)
                message(app.getString(R.string.bell_sync_results, bellDiffText))
                positive(R.string.ok) {
                    this@BellSyncDialog.dismiss()
                    if (activity is MainActivity) activity.reloadTarget()
                }
            }.show()
        }

        // Show the easter-egg bell when the time difference exceeds two hours.
        if (Time.diff(Time.getNow(), bellTime) > Time(2, 0, 0)) {
            b.bellSyncButton.setImageDrawable(R.drawable.illustration_bell_unusual.resolveDrawable(app))
        }

        startCoroutineTimer(repeatMillis = 500) {
            val (bellDiff, multiplier) = actualBellDiff
            val bellDiffText = (if (multiplier == -1) '-' else '+') + bellDiff.stringHMS
            b.bellSyncHowto.text =
                app.getString(R.string.bell_sync_howto, bellTime.stringHM, bellDiffText)
        }
    }
}
