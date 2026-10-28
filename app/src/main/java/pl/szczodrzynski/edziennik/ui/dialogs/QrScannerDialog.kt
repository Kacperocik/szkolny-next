/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-19.
 */

package pl.szczodrzynski.edziennik.ui.dialogs

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.Size
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ui.base.dialog.ViewDialog

class QrScannerDialog(
    activity: AppCompatActivity,
    val onCodeScanned: (text: String) -> Unit,
) : ViewDialog<LinearLayout>(activity) {

    private lateinit var scannerView: DecoratedBarcodeView
    private var preferredContentHeight = 0
    private var scanning = false
    private var previewWidth = 0
    private var previewHeight = 0
    private val refreshPreview = Runnable {
        if (!scanning || !scannerView.isAttachedToWindow)
            return@Runnable
        val preview = scannerView.barcodeView
        if (preview.width <= 0 || preview.height <= 0 ||
            (preview.width == previewWidth && preview.height == previewHeight))
            return@Runnable
        // ZXing caches both framing rectangles when the camera is configured.
        // Reconfigure after resizing so the overlay and decoder use the same current bounds.
        scannerView.pause()
        resumePreview()
    }

    override fun getTitleRes() = R.string.qr_scanner_dialog_title
    override fun getPositiveButtonText() = R.string.close
    override fun hasCloseIconWhenSpaceLimited() = true
    override fun getContentOverflow() = (preferredContentHeight - root.height).coerceAtLeast(0)

    override fun getRootView(): LinearLayout {
        scannerView = DecoratedBarcodeView(activity).apply {
            statusView?.visibility = View.GONE
            barcodeView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (scanning && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) {
                    removeCallbacks(refreshPreview)
                    postDelayed(refreshPreview, 200L)
                }
            }
        }
        val instruction = MaterialTextView(activity).apply {
            setText(R.string.qr_scanner_dialog_instruction)
            TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(24.dp, 12.dp, 24.dp, 16.dp)
        }
        return object : LinearLayout(activity) {
            init {
                orientation = VERTICAL
                addView(scannerView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
                addView(instruction, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                instruction.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                val preferredHeight = minOf(width, 360.dp)
                preferredContentHeight = preferredHeight + instruction.measuredHeight
                val previewHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                    preferredHeight
                } else {
                    minOf(preferredHeight, (MeasureSpec.getSize(heightMeasureSpec) - instruction.measuredHeight).coerceAtLeast(0))
                }
                scannerView.layoutParams.height = previewHeight
                // Reserve the instruction height and keep the QR target square in either orientation.
                val frameSize = (minOf(width, previewHeight) * 0.8f).toInt().coerceAtLeast(1)
                scannerView.barcodeView.framingRectSize = Size(frameSize, frameSize)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    override suspend fun onShow() {
        scannerView.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.text?.let { text ->
                    scanning = false
                    scannerView.removeCallbacks(refreshPreview)
                    scannerView.pause()
                    dismiss()
                    onCodeScanned(text)
                }
            }
        })
        scanning = true
        resumePreview()
    }

    private fun resumePreview() {
        previewWidth = scannerView.barcodeView.width
        previewHeight = scannerView.barcodeView.height
        scannerView.resume()
    }

    override suspend fun onDismiss() {
        scanning = false
        scannerView.removeCallbacks(refreshPreview)
        scannerView.pause()
    }
}
