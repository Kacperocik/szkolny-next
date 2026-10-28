package pl.szczodrzynski.edziennik.ui.crop

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.MenuItemCompat
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageActivity
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.enableExpressiveShapeMorph

/**
 * [CropImageActivity] replacement used for all crop flows. The action bar
 * sits at the bottom when the app bottom toolbar mode
 * ([toolbarAtBottom][pl.szczodrzynski.edziennik.data.config.Config.UI.toolbarAtBottom])
 * is enabled, and at the top otherwise. The crop menu (crop, rotate, flip)
 * is hosted by that toolbar: all actions forced visible, with the crop
 * action styled as a filled primary button.
 */
class AppCropImageActivity : CropImageActivity() {

    private var bottomToolbar: MaterialToolbar? = null
    private var bottomCropView: CropImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // super installs its own content first; setCropImageView() below swaps it.
        super.onCreate(savedInstanceState)

        placeToolbar()
        applySystemBarInsets()
        bottomToolbar?.let { setSupportActionBar(it) }
        applyToolbarCustomizations()
    }

    /**
     * Puts the toolbar below the crop view in bottom bar mode, above it otherwise.
     */
    private fun placeToolbar() {
        val root = findViewById<LinearLayout>(R.id.cropRoot) ?: return
        val toolbar = bottomToolbar ?: return
        root.removeView(toolbar)
        val atBottom = (application as? App)?.config?.ui?.toolbarAtBottom ?: false
        if (atBottom) {
            root.addView(toolbar)
        } else {
            root.addView(toolbar, 0)
        }
    }

    /**
     * Keeps the toolbar clear of the status bar (top mode) and the navigation
     * bar (bottom mode) in edge-to-edge.
     */
    private fun applySystemBarInsets() {
        val root = findViewById<LinearLayout>(R.id.cropRoot) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val atBottom = (application as? App)?.config?.ui?.toolbarAtBottom ?: false
            view.setPadding(0, if (atBottom) 0 else bars.top, 0, if (atBottom) bars.bottom else 0)
            insets
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        // ALWAYS items are placed regardless of the width measurement,
        // so all actions stay on the bar even in portrait.
        for (itemId in listOf(
            R.id.ic_rotate_left_24,
            R.id.ic_rotate_right_24,
            R.id.ic_flip_24,
            R.id.crop_image_menu_crop,
        )) {
            menu.findItem(itemId)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        // Style the crop action as a filled primary button with shape morphing.
        // Note: ActionMenuView resets children's margins when measuring, so the
        // end gap is done with inner padding of a wrapper, which it cannot strip.
        menu.findItem(R.id.crop_image_menu_crop)?.let { cropItem ->
            val density = resources.displayMetrics.density
            val button = MaterialButton(this).apply {
                text = cropItem.title
                setOnClickListener { onOptionsItemSelected(cropItem) }
                enableExpressiveShapeMorph()
            }
            val wrapper = FrameLayout(this).apply {
                setPadding(0, 0, (12 * density).toInt(), 0)
                addView(
                    button,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.CENTER,
                    ),
                )
            }
            MenuItemCompat.setActionView(cropItem, wrapper)
        }
        return true
    }

    override fun setCropImageView(cropImageView: CropImageView) {
        if (bottomCropView == null) {
            setContentView(R.layout.activity_crop)
            bottomToolbar = findViewById(R.id.cropBottomToolbar)
            bottomCropView = findViewById(R.id.cropImageView)
            super.setCropImageView(bottomCropView!!)
        } else {
            super.setCropImageView(cropImageView)
        }
    }

    @Suppress("DEPRECATION")
    private fun cropOptions(): CropImageOptions? {
        val bundle = intent.getBundleExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE) ?: return null
        return bundle.getParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS)
    }

    /**
     * Mirror of the private library customizations, applied to the toolbar.
     */
    private fun applyToolbarCustomizations() {
        val options = cropOptions() ?: return
        supportActionBar?.let { bar ->
            bar.title = options.activityTitle.ifEmpty { "" }
            bar.setDisplayHomeAsUpEnabled(true)
            options.toolbarColor?.let { toolbarColor ->
                bar.setBackgroundDrawable(ColorDrawable(toolbarColor))
            }
            options.toolbarTitleColor?.let { toolbarTitleColor ->
                val spannableTitle: Spannable = SpannableString(bar.title)
                spannableTitle.setSpan(
                    ForegroundColorSpan(toolbarTitleColor),
                    0,
                    spannableTitle.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                bar.title = spannableTitle
            }
            options.toolbarBackButtonColor?.let { backBtnColor ->
                try {
                    val upArrow = ContextCompat.getDrawable(
                        this,
                        R.drawable.ic_arrow_back_24,
                    )
                    upArrow?.colorFilter = PorterDuffColorFilter(backBtnColor, PorterDuff.Mode.SRC_ATOP)
                    bar.setHomeAsUpIndicator(upArrow)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
