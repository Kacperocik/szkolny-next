package pl.szczodrzynski.edziennik

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.graphics.Outline
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavOptions
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.danimahardhika.cafebar.CafeBar
import com.danimahardhika.cafebar.CafeBarTheme
import com.google.android.material.color.MaterialColors
import com.jetradarmobile.snowfall.SnowfallView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.ExpandableDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.descriptionRes
import com.mikepenz.materialdrawer.model.interfaces.nameRes
import com.mikepenz.materialdrawer.model.utils.hiddenInMiniDrawer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.droidsonroids.gif.GifDrawable
import pl.szczodrzynski.edziennik.core.manager.AvailabilityManager.Error.Type
import pl.szczodrzynski.edziennik.core.manager.UserActionManager
import pl.szczodrzynski.edziennik.core.work.AppManagerDetectedEvent
import pl.szczodrzynski.edziennik.core.work.SyncWorker
import pl.szczodrzynski.edziennik.core.work.UpdateStateEvent
import pl.szczodrzynski.edziennik.core.work.UpdateWorker
import pl.szczodrzynski.edziennik.data.api.ERROR_VULCAN_API_DEPRECATED
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.task.MarkAllReadTask
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskAllFinishedEvent
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskErrorEvent
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskFinishedEvent
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskProgressEvent
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskStartedEvent
import pl.szczodrzynski.edziennik.data.api.events.ProfileListEmptyEvent
import pl.szczodrzynski.edziennik.data.api.events.RegisterAvailabilityEvent
import pl.szczodrzynski.edziennik.data.api.events.UserActionRequiredEvent
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.api.szkolny.response.Update
import pl.szczodrzynski.edziennik.data.db.entity.Message
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.data.enums.NavTargetLocation
import pl.szczodrzynski.edziennik.databinding.ActivitySzkolnyBinding
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ext.JsonObject
import pl.szczodrzynski.edziennik.ext.getAppData
import pl.szczodrzynski.edziennik.ext.getEnum
import pl.szczodrzynski.edziennik.ext.getIntOrNull
import pl.szczodrzynski.edziennik.ext.hasUIFeature
import pl.szczodrzynski.edziennik.ext.spinGear360
import pl.szczodrzynski.edziennik.ext.isBeforeYear
import pl.szczodrzynski.edziennik.ext.keys
import pl.szczodrzynski.edziennik.ext.putExtras
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.resolveString
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.ext.shouldArchive
import pl.szczodrzynski.edziennik.ext.takePositive
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ext.toImageHolder
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.ui.dialogs.ChangelogDialog
import pl.szczodrzynski.edziennik.ui.dialogs.ErrorDetailsDialog
import pl.szczodrzynski.edziennik.ui.dialogs.settings.ProfilesDialog
import pl.szczodrzynski.edziennik.ui.dialogs.settings.ProfileConfigDialog
import pl.szczodrzynski.edziennik.ui.dialogs.sync.RegisterUnavailableDialog
import pl.szczodrzynski.edziennik.ui.dialogs.sync.ServerMessageDialog
import pl.szczodrzynski.edziennik.ui.dialogs.sync.SyncViewListDialog
import pl.szczodrzynski.edziennik.ui.dialogs.sync.UpdateAvailableDialog
import pl.szczodrzynski.edziennik.ui.dialogs.sync.UpdateProgressDialog
import pl.szczodrzynski.edziennik.ui.event.EventManualDialog
import pl.szczodrzynski.edziennik.ui.login.LoginActivity
import pl.szczodrzynski.edziennik.ui.main.ErrorSnackbar
import pl.szczodrzynski.edziennik.ui.main.MainSnackbar
import pl.szczodrzynski.edziennik.ui.messages.compose.MessagesComposeFragment
import pl.szczodrzynski.edziennik.ui.messages.list.MessagesFragment
import pl.szczodrzynski.edziennik.ui.settings.navigateSettingsHierarchy
import pl.szczodrzynski.edziennik.ui.timetable.TimetableFragment
import pl.szczodrzynski.edziennik.utils.BigNightUtil
import pl.szczodrzynski.edziennik.utils.PausedNavigationData
import pl.szczodrzynski.edziennik.utils.Utils
import pl.szczodrzynski.edziennik.utils.appManagerIntentList
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time
import pl.szczodrzynski.navlib.NavView
import pl.szczodrzynski.navlib.SystemBarsUtil
import pl.szczodrzynski.navlib.bottomsheet.NavBottomSheet
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetGroup
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.IBottomSheetItem
import pl.szczodrzynski.navlib.drawer.NavDrawer
import pl.szczodrzynski.navlib.drawer.items.DrawerPrimaryItem
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), CoroutineScope {
    companion object {
        private const val TAG = "MainActivity"
    }

    private var job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    val b: ActivitySzkolnyBinding by lazy { ActivitySzkolnyBinding.inflate(layoutInflater) }
    val navView: NavView by lazy { b.navView }
    val drawer: NavDrawer by lazy { navView.drawer }
    val bottomSheet: NavBottomSheet by lazy { navView.bottomSheet }
    val mainSnackbar: MainSnackbar by lazy { MainSnackbar(this) }
    val errorSnackbar: ErrorSnackbar by lazy { ErrorSnackbar(this) }
    private var excusesAvailableInDrawer = false
    val requestHandler by lazy { MainActivityRequestHandler(this) }

    val swipeRefreshLayout: SwipeRefreshLayout by lazy { b.swipeRefreshLayout }

    var onBeforeNavigate: (() -> Boolean)? = null
    private var pausedNavigationData: PausedNavigationData? = null

    val app: App by lazy {
        applicationContext as App
    }

    private val fragmentManager by lazy { supportFragmentManager }
    lateinit var navTarget: NavTarget
        private set
    private var navArguments: Bundle? = null

    private val navBackStack = mutableListOf<Pair<NavTarget, Bundle?>>()
    private var navLoading = true

    /*     ____           _____                _
          / __ \         / ____|              | |
         | |  | |_ __   | |     _ __ ___  __ _| |_ ___
         | |  | | '_ \  | |    | '__/ _ \/ _` | __/ _ \
         | |__| | | | | | |____| | |  __/ (_| | ||  __/
          \____/|_| |_|  \_____|_|  \___|\__,_|\__\__*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("Activity created")

        app.uiManager.applyTheme(this)
        app.uiManager.applyLanguage(this)
        app.buildManager.validateBuild(this)

        if (App.profileId == 0) {
            Timber.i("Profile is not loaded")
            onProfileListEmptyEvent(ProfileListEmptyEvent())
            return
        }

        Timber.i("Profile is valid, inflating views")

        setContentView(b.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (bottomSheet.isOpen) {
                    // the sheet closes itself, no preview
                    return
                }
                if (App.config.ui.openDrawerOnBackPressed && !drawer.isOpen && !drawer.fixedDrawerEnabled()) {
                    // the drawer will slide in following the finger
                    predictiveDrawerReturnAnimator?.cancel()
                    predictiveDrawerReturnAnimator = null
                    predictiveDrawerFraction = 0f
                    predictiveDrawerActive = true
                    return
                }
                if (!shouldPredictSubpageBack()) {
                    return
                }
                val parentTarget = navTarget.popTo ?: navBackStack.lastOrNull()?.first ?: return
                val pages = fragmentManager.fragments.filter { it.id == R.id.fragment && it.isAdded && !it.isRemoving }
                val subpage = pages.lastOrNull { navTarget.fragmentClass?.isInstance(it) == true }?.view ?: return
                val parent = pages.lastOrNull { parentTarget.fragmentClass?.isInstance(it) == true }?.view ?: return
                predictiveSubpageView = subpage
                predictiveParentView = parent
                predictiveSubpageView?.animate()?.cancel()
                predictiveParentView?.animate()?.cancel()
                predictiveCornerRadius = 0f
                predictiveBackActive = true

                predictiveSubpageView?.outlineProvider = predictiveOutlineProvider
                predictiveSubpageView?.clipToOutline = true
                predictiveSubpageView?.invalidateOutline()
                // Opaque for the gesture duration: otherwise the parent page
                // would bleed through the transparent subpage (ghosting).
                predictiveSubpageView?.setBackgroundColor(
                    android.R.attr.colorBackground.resolveAttr(this@MainActivity)
                )

                predictiveParentView?.let { parent ->
                    parent.visibility = View.VISIBLE
                    parent.pivotX = parent.width / 2f
                    parent.pivotY = parent.height / 2f
                    parent.scaleX = predictiveParentStartScale
                    parent.scaleY = predictiveParentStartScale
                    parent.alpha = predictiveParentStartAlpha
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (predictiveDrawerActive) {
                    if (backEvent.swipeEdge != BackEventCompat.EDGE_LEFT) {
                        return
                    }
                    predictiveDrawerFraction = backEvent.progress.coerceIn(0f, 1f)
                    drawer.setSlideOffset(predictiveDrawerFraction)
                    return
                }
                val subpage = predictiveSubpageView
                if (!predictiveBackActive || subpage == null) {
                    return
                }
                val progress = backEvent.progress // 0.0 to 1.0
                val direction = if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                predictiveDirection = direction

                // Leaving page follows the finger across the whole screen...
                subpage.translationX = direction * progress * subpage.width

                // ...rounding its corners while it goes.
                predictiveCornerRadius = progress * predictiveMaxCornerPx
                subpage.invalidateOutline()

                // Page underneath scales up and fades in.
                predictiveParentView?.let { parent ->
                    val parentScale =
                        predictiveParentStartScale + progress * (1f - predictiveParentStartScale)
                    parent.scaleX = parentScale
                    parent.scaleY = parentScale
                    parent.alpha =
                        predictiveParentStartAlpha + progress * (1f - predictiveParentStartAlpha)
                }
            }

            override fun handleOnBackPressed() {
                if (predictiveDrawerActive) {
                    predictiveDrawerActive = false
                    predictiveDrawerFraction = 0f
                    // continues smoothly from the finger position
                    drawer.open()
                    return
                }
                if (predictiveFinishing) {
                    // finish animation already running: swallow extra presses
                    return
                }
                if (!predictiveBackActive) {
                    handleBackPress()
                    return
                }
                predictiveBackActive = false
                val subpage = predictiveSubpageView
                if (subpage == null) {
                    handleBackPress()
                    return
                }
                predictiveFinishing = true
                // Glide the page the rest of the way off-screen, then navigate.
                // No snap-back: the transaction detaches the translated view and
                // its exit animation plays off-screen, invisible.
                subpage.animate()
                    .translationX(predictiveDirection * subpage.width)
                    .setDuration(180L)
                    .withEndAction {
                        predictiveFinishing = false
                        subpage.background = null
                        predictiveJustFinished = true
                        handleBackPress()
                        predictiveJustFinished = false
                    }
                    .start()
                predictiveParentView?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.alpha(1f)
                    ?.setDuration(180L)
                    ?.start()
            }

            override fun handleOnBackCancelled() {
                if (predictiveDrawerActive) {
                    predictiveDrawerActive = false
                    val startFraction = predictiveDrawerFraction
                    predictiveDrawerFraction = 0f
                    predictiveDrawerReturnAnimator?.cancel()
                    predictiveDrawerReturnAnimator =
                        ValueAnimator.ofFloat(startFraction, 0f).apply {
                            duration = 200L
                            addUpdateListener {
                                drawer.setSlideOffset(it.animatedValue as Float)
                            }
                            start()
                        }
                    return
                }
                val subpage = predictiveSubpageView
                if (!predictiveBackActive || subpage == null) {
                    return
                }
                predictiveBackActive = false
                subpage.animate()
                    .translationX(0f)
                    .setDuration(200L)
                    .withEndAction {
                        subpage.clipToOutline = false
                        predictiveCornerRadius = 0f
                        subpage.invalidateOutline()
                        subpage.background = null
                    }
                    .start()

                predictiveParentView?.animate()
                    ?.scaleX(predictiveParentStartScale)
                    ?.scaleY(predictiveParentStartScale)
                    ?.alpha(predictiveParentStartAlpha)
                    ?.setDuration(200L)
                    ?.withEndAction {
                        // subpage stays on top: hide the main page again
                        predictiveParentView?.visibility = View.GONE
                        resetFragmentTransform(predictiveParentView)
                    }
                    ?.start()
            }
        })

        val snackbarAnchor = if (app.config.ui.toolbarAtBottom) b.navView.toolbar.parent as View else null
        mainSnackbar.setCoordinator(b.navView.coordinator, snackbarAnchor)
        errorSnackbar.setCoordinator(b.navView.coordinator, snackbarAnchor)

        val versionBadge = app.buildManager.versionBadge
        navView.nightlyText.isVisible = versionBadge != null
        navView.nightlyText.text = versionBadge
        if (versionBadge != null) {
            navView.nightlyText.background.setTintColor(0xa0ff0000.toInt())
        }

        navLoading = true

        b.navView.apply {
            toolbarAtBottom = app.config.ui.toolbarAtBottom
            val statusBarColor = android.R.attr.colorBackground.resolveAttr(context)
            configSystemBarsUtil(SystemBarsUtil(this@MainActivity).apply {
                this.statusBarColor = statusBarColor
                this.appFullscreen = app.config.ui.appFullscreen
                commit()
            })
            drawer.init(this@MainActivity)

            toolbar.apply {
                enable = true
                enableMenuControls = true
            }

            bottomBar.apply {
                enable = false
                enableMenuControls = false
                fabEnable = false
                fabExtended = false
                fabGravity = Gravity.END
            }

            bottomSheet.apply {
                removeAllItems()
                onCloseListener = {
                    if (!app.config.ui.bottomSheetOpened)
                        app.config.ui.bottomSheetOpened = true
                }
            }

            drawer.apply {
                appFullscreen = app.config.ui.appFullscreen
                accountHeaderVisible = app.config.ui.drawerHeaderVisible
                setAccountHeaderBackground(app.config.ui.headerBackground)

                drawerTargetResolver = { id ->
                    val navTarget = NavTarget.entries.firstOrNull { it.id == id }
                    var current: NavTarget? = navTarget
                    var resultId = id
                    while (current != null) {
                        if (current.location in listOf(
                                NavTargetLocation.DRAWER,
                                NavTargetLocation.DRAWER_BOTTOM,
                                NavTargetLocation.DRAWER_MORE
                            )
                        ) {
                            resultId = current.id
                            break
                        }
                        current = current.popTo
                    }
                    resultId
                }

                drawerProfileListEmptyListener = {
                    onProfileListEmptyEvent(ProfileListEmptyEvent())
                }
                drawerItemSelectedListener = { id, _, item ->
                    if (item is ExpandableDrawerItem) {
                        false
                    } else {
                        val target = NavTarget.getById(id)
                        if (target != null) {
                            navigate(navTarget = target)
                            true
                        } else {
                            false
                        }
                    }
                }
                drawerItemReselectListener = { id, _, _ ->
                    val target = NavTarget.getById(id)
                    if (target != null && target != navTarget) {
                        navigate(navTarget = target)
                    } else {
                        false
                    }
                }
                drawerItemLongClickListener = { id, _, _, view ->
                    if (NavTarget.getById(id) == NavTarget.SETTINGS) {
                        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        val iconView = view?.findViewById<View>(com.mikepenz.materialdrawer.R.id.material_drawer_icon)
                            ?: view?.findViewById<View>(pl.szczodrzynski.navlib.R.id.material_drawer_icon)
                            ?: view
                        iconView?.spinGear360()
                        true
                    } else {
                        false
                    }
                }
                drawerProfileSelectedListener = { id, _, _, _ ->
                    // why is this negated -_-
                    !navigate(profileId = id)
                }
                drawerProfileLongClickListener = { _, profile, _, view ->
                    if (view != null && profile is ProfileDrawerItem) {
                        launch {
                            val appProfile = withContext(Dispatchers.IO) {
                                App.db.profileDao().getByIdNow(profile.identifier.toInt())
                            } ?: return@launch
                            drawer.close()
                            ProfileConfigDialog(this@MainActivity, appProfile).show()
                        }
                        true
                    } else {
                        false
                    }
                }
                drawerProfileImageClickListener = { id, _, current, _ ->
                    if (current) {
                        drawer.close()
                        ProfilesDialog(this@MainActivity).show()
                        true
                    } else {
                        !navigate(profileId = id)
                        drawer.close()
                        true
                    }
                }
                drawerProfileImageLongClickListener = drawerProfileLongClickListener
                drawerProfileSettingClickListener = { id, view -> onProfileSettingClick(id, view) }
                drawerProfileSelectionClickListener = {
                    ProfilesDialog(this@MainActivity).show()
                }

                fixedDrawerCollapsed = app.config.ui.fixedDrawerCollapsed
                fixedDrawerCollapsedChangedListener = { collapsed ->
                    app.config.ui.fixedDrawerCollapsed = collapsed
                }
                miniDrawerVisibleLandscape = null
                miniDrawerVisiblePortrait = app.config.ui.miniMenuVisible
            }
        }

        navTarget = NavTarget.HOME

        if (savedInstanceState != null) {
            intent?.putExtras(savedInstanceState)
            savedInstanceState.clear()
        }

        app.db.profileDao().all.observe(this) { profiles ->
            val allArchived = profiles.all { it.archived }
            drawer.setProfileList(profiles.filter {
                it.id >= 0 && (!it.archived || allArchived)
            }.toMutableList())
            //prepend the archived profile if loaded
            if (app.profile.archived && !allArchived) {
                drawer.prependProfile(Profile(
                    id = app.profile.id,
                    loginStoreId = app.profile.loginStoreId,
                    loginStoreType = app.profile.loginStoreType,
                    name = app.profile.name,
                    subname = "Archiwum - ${app.profile.subname}"
                ).also {
                    it.archived = true
                })
            }
            drawer.currentProfile = App.profileId
        }

        setDrawerItems()

        handleIntent(intent?.extras)

        app.db.metadataDao().unreadCounts.observe(this) { unreadCounters ->
            drawer.setUnreadCounterList(unreadCounters)
        }

        swipeRefreshLayout.setOnRefreshListener { launch { syncCurrentFeature() } }

        SyncWorker.scheduleNext(app)
        UpdateWorker.scheduleNext(app)

        // if loaded profile is archived, switch to the up-to-date version of it
        if (app.profile.archived) {
            launch {
                if (app.profile.archiveId != null) {
                    val profile = withContext(Dispatchers.IO) {
                        app.db.profileDao().getNotArchivedOf(app.profile.archiveId!!)
                    }
                    if (profile != null)
                        navigate(profile = profile)
                    else
                        navigate(profileId = 0)
                } else {
                    navigate(profileId = 0)
                }
            }
        }

        // APP BACKGROUND
        setAppBackground()

        // IT'S WINTER MY DUDES
        val today = Date.getToday()
        if ((today.month / 3 % 4 == 0) && app.config.ui.snowfall) {
            b.rootFrame.addView(layoutInflater.inflate(R.layout.snowfall, b.rootFrame, false))
        } else if (app.config.ui.eggfall && BigNightUtil().isDataWielkanocyNearDzisiaj()) {
            val eggfall = layoutInflater.inflate(
                R.layout.eggfall,
                b.rootFrame,
                false
            ) as SnowfallView
            eggfall.setSnowflakeBitmaps(listOf(
                BitmapFactory.decodeResource(resources, R.drawable.egg1),
                BitmapFactory.decodeResource(resources, R.drawable.egg2),
                BitmapFactory.decodeResource(resources, R.drawable.egg3),
                BitmapFactory.decodeResource(resources, R.drawable.egg4),
                BitmapFactory.decodeResource(resources, R.drawable.egg5),
                BitmapFactory.decodeResource(resources, R.drawable.egg6)
            ))
            b.rootFrame.addView(eggfall)
        }

        // WHAT'S NEW DIALOG
        if (app.config.appVersion < BuildConfig.VERSION_CODE) {
            // force an AppSync after update
            app.config.sync.lastAppSync = 0L
            ChangelogDialog(this).show()
            app.config.appVersion = BuildConfig.VERSION_CODE
        }

        // RATE SNACKBAR
        if (app.config.appRateSnackbarTime != 0L && app.config.appRateSnackbarTime <= System.currentTimeMillis()) {
            navView.coordinator.postDelayed({
                CafeBar.builder(this)
                    .theme(CafeBarTheme.Custom(R.attr.colorSurfaceInverse.resolveAttr(this)))
                    .content(R.string.rate_snackbar_text)
                    .icon(CommunityMaterial.Icon3.cmd_star_outline.toDrawable())
                    .positiveText(R.string.rate_snackbar_positive)
                    .positiveColor(-0xb350b0)
                    .negativeText(R.string.rate_snackbar_negative)
                    .negativeColor(0xff666666.toInt())
                    .neutralText(R.string.rate_snackbar_neutral)
                    .neutralColor(0xff666666.toInt())
                    .onPositive { cafeBar ->
                        Utils.openGooglePlay(this)
                        cafeBar.dismiss()
                        app.config.appRateSnackbarTime = 0
                    }
                    .onNegative { cafeBar ->
                        Toast.makeText(this,
                            R.string.rate_snackbar_negative_message,
                            Toast.LENGTH_LONG).show()
                        cafeBar.dismiss()
                        app.config.appRateSnackbarTime = 0
                    }
                    .onNeutral { cafeBar ->
                        Toast.makeText(this, R.string.ok, Toast.LENGTH_LONG).show()
                        cafeBar.dismiss()
                        app.config.appRateSnackbarTime =
                            System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000
                    }
                    .autoDismiss(false)
                    .swipeToDismiss(true)
                    .floating(true)
                    .show()
            }, 10000)
        }

        // CONTEXT MENU ITEMS
        bottomSheet.removeAllItems()

        val staticItems = mutableListOf<IBottomSheetItem<*>>()
        staticItems.add(
            BottomSheetPrimaryItem(false)
                .withTitle(R.string.menu_sync)
                .withIcon(CommunityMaterial.Icon.cmd_download_outline)
                .withGroup(BottomSheetGroup.ACTIONS)
                .withOnClickListener {
                    bottomSheet.close()
                    SyncViewListDialog(this, navTarget).show()
                }
        )

        for (target in NavTarget.values()) {
            if (target.isAvailable?.invoke(app.profile) == false)
                continue
            if (target.location != NavTargetLocation.BOTTOM_SHEET)
                continue
            if (target.devModeOnly && !App.devMode)
                continue
            staticItems.add(target.toBottomSheetItem(this))
        }
        bottomSheet.appendItems(*staticItems.toTypedArray())
    }

    fun onProfileSettingClick(itemId: Int, view: View? = null): Boolean {
        when (val item = NavTarget.getById(itemId)) {
            NavTarget.PROFILE_ADD -> {
                requestHandler.requestLogin()
            }
            NavTarget.PROFILE_SYNC_ALL -> {
                EdziennikTask.sync().enqueue(this)
            }
            NavTarget.PROFILE_MARK_AS_READ -> {
                markAllAsRead()
            }
            else -> {
                navigate(navTarget = item)
            }
        }
        return false
    }

    /*     _____
          / ____|
         | (___  _   _ _ __   ___
          \___ \| | | | '_ \ / __|
          ____) | |_| | | | | (__
         |_____/ \__, |_| |_|\___|
                  __/ |
                 |__*/
    private suspend fun syncCurrentFeature() {
        if (app.profile.archived) {
            SimpleDialog<Unit>(this) {
                title(R.string.profile_archived_title)
                message(
                    R.string.profile_archived_text,
                    app.profile.studentSchoolYearStart,
                    app.profile.studentSchoolYearStart + 1
                )
                positive(R.string.ok)
            }.show()
            swipeRefreshLayout.isRefreshing = false
            return
        }
        if (app.profile.shouldArchive()) {
            SimpleDialog<Unit>(this) {
                title(R.string.profile_archiving_title)
                message(
                    R.string.profile_archiving_format,
                    app.profile.dateYearEnd.formattedString
                )
                positive(R.string.ok)
            }.show()
        }
        if (app.profile.isBeforeYear()) {
            SimpleDialog<Unit>(this) {
                title(R.string.profile_year_not_started_title)
                message(
                    R.string.profile_year_not_started_format,
                    app.profile.dateSemester1Start.formattedString
                )
                positive(R.string.ok)
            }.show()
            swipeRefreshLayout.isRefreshing = false
            return
        }

        val error = withContext(Dispatchers.IO) {
            app.availabilityManager.check(app.profile)
        }
        when (error?.type) {
            Type.NOT_AVAILABLE -> {
                swipeRefreshLayout.isRefreshing = false
                navigate(navTarget = NavTarget.HOME)
                RegisterUnavailableDialog(this, error.status!!).show()
                return
            }
            Type.API_ERROR -> {
                errorSnackbar.addError(error.apiError!!).show()
                return
            }
            Type.NO_API_ACCESS -> {
                Toast.makeText(this, R.string.error_no_api_access, Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }

        swipeRefreshLayout.isRefreshing = true
        Toast.makeText(this, fragmentToSyncName(navTarget), Toast.LENGTH_SHORT).show()
        val featureType = when (navTarget) {
            NavTarget.MESSAGES -> when (MessagesFragment.pageSelection) {
                Message.TYPE_SENT -> FeatureType.MESSAGES_SENT
                else -> FeatureType.MESSAGES_INBOX
            }
            else -> navTarget.featureType
        }
        val arguments = when (navTarget) {
            NavTarget.TIMETABLE -> JsonObject("weekStart" to TimetableFragment.pageSelection?.weekStart?.stringY_m_d)
            else -> null
        }
        EdziennikTask.syncProfile(
            App.profileId,
            featureType?.let { setOf(it) },
            arguments = arguments
        ).enqueue(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onUpdateEvent(event: Update) {
        EventBus.getDefault().removeStickyEvent(event)
        UpdateAvailableDialog(this, event).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onUpdateStateEvent(event: UpdateStateEvent) {
        if (!event.running)
            return
        EventBus.getDefault().removeStickyEvent(event)
        UpdateProgressDialog(this, event.update ?: return, event.downloadId).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onRegisterAvailabilityEvent(event: RegisterAvailabilityEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        val error = app.availabilityManager.check(app.profile, cacheOnly = true)
        if (error != null) {
            RegisterUnavailableDialog(this, error.status!!).show()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskStartedEvent(event: ApiTaskStartedEvent) {
        swipeRefreshLayout.isRefreshing = true
        if (event.profileId == App.profileId) {
            navView.toolbar.apply {
                setSubtitleTextColor(MaterialColors.getColor(this, R.attr.colorSecondary))
                subtitle = getString(R.string.toolbar_subtitle_syncing)
            }
            navView.showToolbarProgress()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProfileListEmptyEvent(event: ProfileListEmptyEvent) {
        app.config.loginFinished = false
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onApiTaskProgressEvent(event: ApiTaskProgressEvent) {
        if (event.profileId == App.profileId) {
            navView.toolbar.apply {
                setSubtitleTextColor(MaterialColors.getColor(this, R.attr.colorSecondary))
                subtitle = if (event.progress < 0f)
                    event.progressText ?: ""
                else
                    getString(
                        R.string.toolbar_subtitle_syncing_format,
                        event.progress.roundToInt(),
                        event.progressText ?: "",
                    )

            }
            navView.showToolbarProgress(event.progress)
        }
    }

    fun markAllAsRead(profileId: Int? = null) {
        MarkAllReadTask(
            profileId,
            app.config.sync.markReadMessagesAndAnnouncements,
        ).enqueue(this)
    }

    fun updateLastSyncSubtitle() {
        if (swipeRefreshLayout.isRefreshing) return
        navView.hideToolbarProgress()
        val profileId = App.profileId
        if (profileId == 0) {
            navView.toolbar.subtitle = null
            return
        }
        val lastSync = app.config[profileId].sync.lastSuccessfulSync.takeIf { it > 0 }
            ?: app.db.endpointTimerDao().getLastSyncNow(profileId)?.takeIf { it > 0 }
        if (lastSync != null) {
            val dateStr = Date.fromMillis(lastSync).formattedString
            val timeStr = Time.fromMillis(lastSync).stringHM
            navView.toolbar.setSubtitleTextColor(MaterialColors.getColor(navView.toolbar, R.attr.colorSecondary))
            navView.toolbar.subtitle = "$dateStr, $timeStr"
        } else {
            navView.toolbar.subtitle = null
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onApiTaskFinishedEvent(event: ApiTaskFinishedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        if (event.profileId > 0) {
            app.config[event.profileId].sync.lastSuccessfulSync = System.currentTimeMillis()
        }
        if (event.profileId == App.profileId) {
            updateLastSyncSubtitle()
            if (excusesAvailableInDrawer != (NavTarget.EXCUSES.isAvailable?.invoke(app.profile) == true)) {
                setDrawerItems()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onApiTaskAllFinishedEvent(event: ApiTaskAllFinishedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        swipeRefreshLayout.isRefreshing = false
        updateLastSyncSubtitle()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onApiTaskErrorEvent(event: ApiTaskErrorEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        if (event.error.errorCode == ERROR_VULCAN_API_DEPRECATED) {
            if (event.error.profileId != App.profileId)
                return
            ErrorDetailsDialog(this, listOf(event.error)).show()
        }
        updateLastSyncSubtitle()
        mainSnackbar.dismiss()
        errorSnackbar.addError(event.error).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onAppManagerDetectedEvent(event: AppManagerDetectedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        if (app.config.sync.dontShowAppManagerDialog)
            return
        SimpleDialog<Unit>(this) {
            title(R.string.app_manager_dialog_title)
            message(R.string.app_manager_dialog_text)
            positive(R.string.ok) {
                try {
                    for (intent in appManagerIntentList) {
                        if (packageManager.resolveActivity(intent,
                                PackageManager.MATCH_DEFAULT_ONLY) != null
                        ) {
                            startActivity(intent)
                        }
                    }
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (e: Exception) {
                        Timber.e(e)
                        Toast.makeText(this@MainActivity, R.string.app_manager_open_failed, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
            neutral(R.string.dont_ask_again) {
                app.config.sync.dontShowAppManagerDialog = true
            }
            cancelable(false)
        }.show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUserActionRequiredEvent(event: UserActionRequiredEvent) {
        app.userActionManager.execute(this, event, UserActionManager.UserActionCallback())
    }

    private fun fragmentToSyncName(navTarget: NavTarget): Int {
        return when (navTarget) {
            NavTarget.TIMETABLE -> R.string.sync_feature_timetable
            NavTarget.AGENDA -> R.string.sync_feature_agenda
            NavTarget.GRADES -> R.string.sync_feature_grades
            NavTarget.HOMEWORK -> R.string.sync_feature_homework
            NavTarget.BEHAVIOUR -> R.string.sync_feature_notices
            NavTarget.ATTENDANCE -> R.string.sync_feature_attendance
            NavTarget.MESSAGES -> when (MessagesFragment.pageSelection) {
                Message.TYPE_SENT -> R.string.sync_feature_messages_outbox
                else -> R.string.sync_feature_messages_inbox
            }
            NavTarget.ANNOUNCEMENTS -> R.string.sync_feature_announcements
            else -> R.string.sync_feature_syncing_all
        }
    }

    /*    _____       _             _
         |_   _|     | |           | |
           | |  _ __ | |_ ___ _ __ | |_ ___
           | | | '_ \| __/ _ \ '_ \| __/ __|
          _| |_| | | | ||  __/ | | | |_\__ \
         |_____|_| |_|\__\___|_| |_|\__|__*/
    private val intentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleIntent(intent?.extras)
        }
    }

    fun handleIntent(extras: Bundle?) {
        Timber.d("handleIntent() ${extras?.keySet()}")

        val intentProfileId = extras.getIntOrNull("profileId").takePositive()
        var intentNavTarget = extras.getEnum<NavTarget>("fragmentId")

        if (extras?.containsKey("action") == true) {
            val handled = when (extras.getString("action")) {
                "updateRequest" -> {
                    UpdateAvailableDialog(this, app.config.update).show()
                    true
                }
                "serverMessage" -> {
                    ServerMessageDialog(
                        this,
                        extras.getString("serverMessageTitle") ?: getString(R.string.app_name),
                        extras.getString("serverMessageText") ?: ""
                    ).show()
                    true
                }
                "feedbackMessage" -> {
                    intentNavTarget = NavTarget.FEEDBACK
                    false
                }
                "userActionRequired" -> {
                    val event = UserActionRequiredEvent(
                        profileId = extras.getInt("profileId"),
                        type = extras.getEnum<UserActionRequiredEvent.Type>("type") ?: return,
                        params = extras.getBundle("params") ?: return,
                        errorText = 0,
                    )
                    app.userActionManager.execute(this,
                        event,
                        UserActionManager.UserActionCallback())
                    true
                }
                "createManualEvent" -> {
                    val date = extras.getString("eventDate")
                        ?.let { Date.fromY_m_d(it) }
                        ?: Date.getToday()
                    EventManualDialog(
                        this,
                        App.profileId,
                        defaultDate = date
                    ).show()
                    true
                }
                else -> false
            }
            if (handled && !navLoading) {
                return
            }
        }

        if (extras?.containsKey("reloadProfileId") == true) {
            val reloadProfileId = extras.getIntOrNull("reloadProfileId").takePositive()
            if (reloadProfileId == null || app.profile.id == reloadProfileId) {
                reloadTarget()
                return
            }
        }

        extras?.remove("profileId")
        extras?.remove("fragmentId")
        extras?.remove("reloadProfileId")

        /*if (intentTargetId == -1 && navController.currentDestination?.id == R.id.loadingFragment) {
            intentTargetId = navTarget.id
        }*/

        if (navLoading)
            b.fragment.removeAllViews()

        when {
            app.profile.id == 0 -> navigate(
                profileId = intentProfileId ?: app.config.lastProfileId,
                navTarget = intentNavTarget,
                args = extras,
            )
            intentProfileId != null -> navigate(
                profileId = intentProfileId,
                navTarget = intentNavTarget,
                args = extras,
            )
            intentNavTarget != null -> navigate(
                navTarget = intentNavTarget,
                args = extras,
            )
            navLoading -> navigate()
            else -> drawer.currentProfile = app.profile.id
        }
        navLoading = false
    }

    override fun recreate() {
        recreate(navTarget)
    }

    fun recreate(navTarget: NavTarget) {
        recreate(navTarget, null)
    }

    fun recreate(navTarget: NavTarget? = null, arguments: Bundle? = null) {
        val intent = Intent(this, MainActivity::class.java)
        if (arguments != null)
            intent.putExtras(arguments)
        if (navTarget != null) {
            intent.putExtras("fragmentId" to navTarget)
        }
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        startActivity(intent)
    }

    override fun onStart() {
        Timber.i("Activity started")
        super.onStart()
    }

    override fun onStop() {
        Timber.i("Activity stopped")
        super.onStop()
    }

    override fun onResume() {
        Timber.i("Activity resumed")
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_MAIN)
        ContextCompat.registerReceiver(
            this,
            intentReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        EventBus.getDefault().register(this)
        updateLastSyncSubtitle()
        super.onResume()
    }

    override fun onPause() {
        Timber.i("Activity paused")
        unregisterReceiver(intentReceiver)
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    override fun onDestroy() {
        Timber.i("Activity destroyed")
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putExtras("fragmentId" to navTarget)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent?.extras)
    }

    @Suppress("deprecation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        requestHandler.handleResult(requestCode, resultCode, data)
    }

    /*    _                     _                  _   _               _
         | |                   | |                | | | |             | |
         | |     ___   __ _  __| |  _ __ ___   ___| |_| |__   ___   __| |___
         | |    / _ \ / _` |/ _` | | '_ ` _ \ / _ \ __| '_ \ / _ \ / _` / __|
         | |___| (_) | (_| | (_| | | | | | | |  __/ |_| | | | (_) | (_| \__ \
         |______\___/ \__,_|\__,_| |_| |_| |_|\___|\__|_| |_|\___/ \__,_|__*/
    val navOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.task_open_enter) // new fragment enter
        .setExitAnim(R.anim.task_open_exit) // old fragment exit
        .setPopEnterAnim(R.anim.task_close_enter) // old fragment enter back
        .setPopExitAnim(R.anim.task_close_exit) // new fragment exit
        .build()

    private fun canNavigate(): Boolean = onBeforeNavigate?.invoke() != false

    fun resumePausedNavigation(): Boolean {
        val data = pausedNavigationData ?: return false
        navigate(
            profileId = data.profileId,
            navTarget = data.navTarget,
            args = data.args,
            skipBeforeNavigate = true,
        )
        pausedNavigationData = null
        return true
    }

    fun navigate(
        profileId: Int? = null,
        profile: Profile? = null,
        navTarget: NavTarget? = null,
        args: Bundle? = null,
        skipBeforeNavigate: Boolean = false,
    ): Boolean {
        Timber.d("navigate(profileId = ${profile?.id ?: profileId}, target = ${navTarget?.name}, args = $args)")
        if (!(skipBeforeNavigate || navTarget == this.navTarget) && !canNavigate()) {
            bottomSheet.close()
            drawer.close()
            // restore the previous profile if changing it with the drawer
            // well, it still does not change the toolbar profile image,
            // but that's now NavView's problem, not mine.
            drawer.currentProfile = App.profile.id
            pausedNavigationData = PausedNavigationData(profileId, navTarget, args)
            return false
        }

        val loadNavTarget = navTarget ?: this.navTarget
        if (profile != null && profile.id != App.profileId) {
            navigateImpl(profile, loadNavTarget, args, profileChanged = true)
            return true
        }
        if (profileId != null && profileId != App.profileId) {
            app.profileLoad(profileId) {
                navigateImpl(it, loadNavTarget, args, profileChanged = true)
            }
            return true
        }
        navigateImpl(App.profile, loadNavTarget, args, profileChanged = false)
        return true
    }

    private fun resolveNavTarget(target: NavTarget): NavTarget {
        if (target == NavTarget.AGENDA && !app.config.labOldAgenda) {
            return NavTarget.AGENDA_NEW
        }
        if (target == NavTarget.AGENDA_NEW && app.config.labOldAgenda) {
            return NavTarget.AGENDA
        }
        return target
    }

    private fun navigateImpl(
        profile: Profile,
        rawNavTarget: NavTarget,
        args: Bundle?,
        profileChanged: Boolean,
    ) {
        hideKeyboard()
        if (rawNavTarget == NavTarget.MESSAGE &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            profile.config.ui.messagesTwoColumnLandscape
        ) {
            if (args?.containsKey("sentDate") == true) {
                MessagesFragment.pageSelection = 1
            }
            MessagesFragment.pendingDetailArgs = args
            navigateImpl(profile, NavTarget.MESSAGES, args, profileChanged)
            return
        }
        val navTarget = resolveNavTarget(rawNavTarget)
        Timber.d("navigateImpl(profileId = ${profile.id}, target = ${navTarget.name}, args = $args)")

        if (navTarget.isAvailable?.invoke(profile) == false ||
            (navTarget.featureType != null && !profile.hasUIFeature(navTarget.featureType))) {
            navigateImpl(profile, NavTarget.HOME, args, profileChanged)
            return
        }

        if (profileChanged) {
            if (App.profileId != profile.id)
                app.profileLoad(profile)
            MessagesFragment.pageSelection = -1
            // set new drawer items for this profile
            setDrawerItems()

            val previousArchivedId = if (app.profile.archived) app.profile.id else null
            if (previousArchivedId != null) {
                // prevents accidentally removing the first item if the archived profile is not shown
                drawer.removeProfileById(previousArchivedId)
            }
            if (profile.archived) {
                // add the same profile but with a different name
                // (other fields are not needed by the drawer)
                drawer.prependProfile(Profile(
                    id = profile.id,
                    loginStoreId = profile.loginStoreId,
                    loginStoreType = profile.loginStoreType,
                    name = profile.name,
                    subname = "Archiwum - ${profile.subname}"
                ).also {
                    it.archived = true
                })
            }

            // the drawer profile is updated automatically when the drawer item is clicked
            // update it manually when switching profiles from other source
            //if (drawer.currentProfile != app.profile.id)
            drawer.currentProfile = App.profileId
        }

        val arguments = args
            ?: navBackStack.firstOrNull { it.first == navTarget }?.second
            ?: Bundle()
        swipeRefreshLayout.isEnabled = false
        bottomSheet.close()
        bottomSheet.removeAllContextual()
        drawer.close()
        val drawerSelectionId = when (navTarget) {
            NavTarget.SETTINGS_THEME,
            NavTarget.SETTINGS_REGISTER,
            NavTarget.SETTINGS_SYNC,
            NavTarget.SETTINGS_ABOUT -> NavTarget.SETTINGS.id
            NavTarget.MESSAGE,
            NavTarget.MESSAGE_COMPOSE -> NavTarget.MESSAGES.id
            else -> navTarget.id
        }
        if (drawer.getSelection() != drawerSelectionId)
            drawer.setSelection(drawerSelectionId, fireOnClick = false)
        navView.toolbar.setTitle(navTarget.titleRes ?: navTarget.nameRes)
        updateLastSyncSubtitle()
        navView.bottomBar.fabEnable = false
        navView.bottomBar.fabExtended = false
        navView.bottomBar.setFabOnClickListener(null)

        val previousNavTarget = this.navTarget
        Timber.d("Navigating from ${previousNavTarget.name} to ${navTarget.name}")

        val fragment = navTarget.fragmentClass?.getDeclaredConstructor()?.newInstance() ?: return
        fragment.arguments = arguments
        val transaction = fragmentManager.beginTransaction()
        var isPop = false

        if (navTarget == this.navTarget) {
            // just reload the current target
            transaction.setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out
            )
        } else {
            navBackStack.keys().lastIndexOf(navTarget).let {
                if (it == -1)
                    return@let navTarget
                // pop the back stack up until that target
                isPop = true
                if (!predictiveJustFinished) {
                    transaction.setCustomAnimations(
                        R.anim.task_close_enter,
                        R.anim.task_close_exit
                    )
                }

                // navigating grades_add -> grades
                // navTarget == grades_add
                // navBackStack = [home, grades, grades_editor]
                // it == 1
                //
                // navTarget = target
                // remove 1
                // remove 2
                val popCount = navBackStack.size - it
                for (i in 0 until popCount) {
                    navBackStack.removeAt(navBackStack.lastIndex)
                }
                this.navTarget = navTarget
                this.navArguments = arguments

                return@let null
            }?.let {
                // target is neither current nor in the back stack
                // so navigate to it
                transaction.setCustomAnimations(
                    R.anim.task_open_enter,
                    R.anim.task_open_exit
                )
                navBackStack.add(this.navTarget to this.navArguments)
                this.navTarget = navTarget
                this.navArguments = arguments
            }
        }

        if (navTarget.popTo == NavTarget.HOME) {
            // if the current has popToHome, let only home be in the back stack
            // probably `if (navTarget.popToHome)` in popBackStack() is not needed now
            val popCount = navBackStack.size - 1
            for (i in 0 until popCount) {
                navBackStack.removeAt(navBackStack.lastIndex)
            }
        }

        Timber.d("Current fragment ${navTarget.name}, back stack:")
        navBackStack.forEachIndexed { index, item ->
            Timber.d(" - $index: ${item.first.name}")
        }

        val stackedFragments = fragmentManager.fragments.filter { it.id == R.id.fragment }
        val currentFrag = fragmentManager.findFragmentById(R.id.fragment)
        val isSendingMessage = navTarget == NavTarget.MESSAGE && previousNavTarget == NavTarget.MESSAGE_COMPOSE
        val existingFrag = if (isPop && !profileChanged && !isSendingMessage) {
            navTarget.fragmentClass.let { cls -> stackedFragments.lastOrNull { cls.isInstance(it) } }
        } else {
            null
        }
        when {
            existingFrag != null && existingFrag != currentFrag -> {
                val existingIndex = stackedFragments.indexOf(existingFrag)
                for (i in existingIndex + 1 until stackedFragments.size) {
                    transaction.remove(stackedFragments[i])
                }
                transaction.show(existingFrag)
            }
            isSendingMessage && !profileChanged -> {
                val messagesFrag = stackedFragments.lastOrNull { it is MessagesFragment }
                if (messagesFrag != null) {
                    val messagesIndex = stackedFragments.indexOf(messagesFrag)
                    for (i in messagesIndex + 1 until stackedFragments.size) {
                        transaction.remove(stackedFragments[i])
                    }
                    transaction.hide(messagesFrag)
                    transaction.add(R.id.fragment, fragment)
                } else {
                    transaction.replace(R.id.fragment, fragment)
                }
            }
            fragmentManager.navigateSettingsHierarchy(
                transaction, R.id.fragment, previousNavTarget, navTarget, fragment, profileChanged,
            ) -> Unit
            currentFrag != null && !profileChanged && (
                (navTarget == NavTarget.MESSAGE && previousNavTarget == NavTarget.MESSAGES) ||
                (navTarget == NavTarget.MESSAGE_COMPOSE)
            ) -> {
                // Keep the parent available beneath the transparent subpage for predictive back.
                transaction.add(R.id.fragment, fragment)
                transaction.hide(currentFrag)
            }
            else -> {
                stackedFragments.forEach {
                    if (it != currentFrag) {
                        transaction.remove(it)
                    }
                }
                transaction.replace(R.id.fragment, fragment)
            }
        }
        transaction.commitAllowingStateLoss()
        resetFragmentTransform(findViewById(R.id.fragment))

        // TASK DESCRIPTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val bm = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_v5)

            @Suppress("deprecation")
            val taskDesc = ActivityManager.TaskDescription(
                if (navTarget == NavTarget.HOME)
                    getString(R.string.app_name)
                else
                    getString(R.string.app_task_format, getString(navTarget.nameRes)),
                bm,
                R.attr.colorPrimary.resolveAttr(this)
            )
            setTaskDescription(taskDesc)
        }
        return
    }

    fun reloadTarget() = navigate()

    private fun popBackStack(skipBeforeNavigate: Boolean = false): Boolean {
        if (navBackStack.size == 0) {
            return false
        }
        // TODO back stack argument support
        if (navTarget.popTo != null) {
            navigate(
                navTarget = navTarget.popTo,
                skipBeforeNavigate = skipBeforeNavigate,
            )
        } else {
            navBackStack.last().let {
                navigate(
                    navTarget = it.first,
                    args = it.second,
                    skipBeforeNavigate = skipBeforeNavigate,
                )
            }
        }
        return true
    }

    fun navigateUp(skipBeforeNavigate: Boolean = false) {
        if (!popBackStack(skipBeforeNavigate)) {
            finish()
        }
    }

    /**
     * Use the NavLib's menu button ripple to gain user attention
     * that something has changed in the bottom sheet.
     */
    fun gainAttention() {
        if (app.config.ui.bottomSheetOpened)
            return
    }

    fun gainAttentionFAB() {
        navView.bottomBar.fabExtended = false

        b.navView.postDelayed({
            navView.bottomBar.fabExtended = true
        }, 1000)

        b.navView.postDelayed({
            navView.bottomBar.fabExtended = false
        }, 3000)
    }

    fun hideFab() = navView.bottomBar.hideFab()
    fun showFab() = navView.bottomBar.showFab()

    fun setAppBackground() {
        try {
            b.root.background = app.config.ui.appBackground?.let {
                if (it.endsWith(".gif"))
                    GifDrawable(it)
                else
                    BitmapDrawable.createFromPath(it)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    /*    _____                                _ _
         |  __ \                              (_) |
         | |  | |_ __ __ ___      _____ _ __   _| |_ ___ _ __ ___  ___
         | |  | | '__/ _` \ \ /\ / / _ \ '__| | | __/ _ \ '_ ` _ \/ __|
         | |__| | | | (_| |\ V  V /  __/ |    | | ||  __/ | | | | \__ \
         |_____/|_|  \__,_| \_/\_/ \___|_|    |_|\__\___|_| |_| |_|__*/
    private fun createDrawerItem(target: NavTarget, level: Int = 1): IDrawerItem<*> {
        val item = when {
            // target.subItems != null -> ExpandableDrawerItem()
            level > 1 -> SecondaryDrawerItem()
            else -> DrawerPrimaryItem()
        }

        item.also {
            it.identifier = target.id.toLong()
            it.nameRes = target.nameRes
            it.descriptionRes = target.descriptionRes ?: -1
            it.icon = target.icon?.toImageHolder()
            val inMiniMenu = app.config.ui.miniMenuButtons.contains(target) ||
                    ((target == NavTarget.AGENDA || target == NavTarget.AGENDA_NEW) &&
                     (app.config.ui.miniMenuButtons.contains(NavTarget.AGENDA) || app.config.ui.miniMenuButtons.contains(NavTarget.AGENDA_NEW)))
            it.hiddenInMiniDrawer = !inMiniMenu
            if (it is DrawerPrimaryItem)
                it.appTitle = target.titleRes?.resolveString(this)
            if (/* it is ColorfulBadgeable && */ target.badgeType != null)
                it.badgeStyle = drawer.badgeStyle
            it.isSelectedBackgroundAnimated = false
            it.level = level
        }
        if (target.badgeType != null)
            drawer.addUnreadCounterType(target.badgeType.id, target.id)

        /* item.subItems = target.subItems?.map {
            createDrawerItem(it, level + 1)
        }?.toMutableList() ?: mutableListOf() */

        return item
    }

    fun setDrawerItems() {
        excusesAvailableInDrawer = NavTarget.EXCUSES.isAvailable?.invoke(app.profile) == true
        Timber.d("setDrawerItems() app.profile = ${app.profile}")
        val drawerItems = arrayListOf<IDrawerItem<*>>()
        val drawerItemsMore = arrayListOf<IDrawerItem<*>>()
        val drawerItemsMoreFallback = arrayListOf<IDrawerItem<*>>()
        val drawerItemsBottom = arrayListOf<IDrawerItem<*>>()
        val drawerProfiles = arrayListOf<ProfileSettingDrawerItem>()

        for (target in NavTarget.values()) {
            if (target.isAvailable?.invoke(app.profile) == false)
                continue
            if (target.devModeOnly && !App.devMode)
                continue
            if (target.featureType != null && !app.profile.hasUIFeature(target.featureType))
                continue

            val isOldAgenda = app.config.labOldAgenda
            if (target == NavTarget.AGENDA && !isOldAgenda) continue
            if (target == NavTarget.AGENDA_NEW && isOldAgenda) continue

            val targetLocation = if (target == NavTarget.AGENDA && isOldAgenda) NavTargetLocation.DRAWER else target.location

            val isTargetInMore = target in app.config.ui.drawerMoreButtons ||
                    ((target == NavTarget.AGENDA || target == NavTarget.AGENDA_NEW) &&
                     (NavTarget.AGENDA in app.config.ui.drawerMoreButtons || NavTarget.AGENDA_NEW in app.config.ui.drawerMoreButtons))

            when (targetLocation) {
                NavTargetLocation.DRAWER -> {
                    if (isTargetInMore) {
                        drawerItemsMore += createDrawerItem(target, level = 2)
                    } else {
                        drawerItems += createDrawerItem(target, level = 1)
                    }
                }
                NavTargetLocation.DRAWER_MORE -> {
                    when {
                        target in NavTarget.drawerMoreConfigurableTargets && !isTargetInMore ->
                            drawerItems += createDrawerItem(target, level = 1)
                        target in NavTarget.drawerMoreConfigurableTargets ->
                            drawerItemsMore += createDrawerItem(target, level = 2)
                        else ->
                            drawerItemsMoreFallback += createDrawerItem(target, level = 2)
                    }
                }
                NavTargetLocation.DRAWER_BOTTOM -> {
                    drawerItemsBottom += createDrawerItem(target, level = 1)
                }
                NavTargetLocation.PROFILE_LIST -> {
                    drawerProfiles += ProfileSettingDrawerItem().also {
                        it.identifier = target.id.toLong()
                        it.nameRes = target.nameRes
                        it.descriptionRes = target.descriptionRes ?: -1
                        it.icon = target.icon?.toImageHolder()
                    }
                }
                else -> continue
            }
        }

        if (drawerItemsMore.isEmpty()) {
            drawerItems += drawerItemsMoreFallback.mapNotNull {
                NavTarget.getById(it.identifier.toInt())?.let { target ->
                    createDrawerItem(target, level = 1)
                }
            }
        } else {
            drawerItemsMore += drawerItemsMoreFallback
            drawerItems += ExpandableDrawerItem().also {
                it.identifier = -1L
                it.nameRes = R.string.menu_more
                it.icon = CommunityMaterial.Icon.cmd_dots_horizontal.toImageHolder()
                it.subItems = drawerItemsMore.toMutableList()
                it.isSelectedBackgroundAnimated = false
                it.isSelectable = false
            }
        }
        drawerItems += DividerDrawerItem()
        drawerItems += drawerItemsBottom

        // seems that this cannot be open, because the itemAdapter has Profile items
        // instead of normal Drawer items...
        drawer.profileSelectionClose()
        drawer.setItems(*drawerItems.toTypedArray())
        drawer.removeAllProfileSettings()
        drawer.addProfileSettings(*drawerProfiles.toTypedArray())
    }

    fun handleBackPress() {
        if (App.config.ui.openDrawerOnBackPressed) {
            if (drawer.isOpen)
                navigateUp()
            else if (!navView.onBackPressed())
                drawer.open()
        } else {
            if (!navView.onBackPressed())
                navigateUp()
        }
    }

    private var predictiveBackActive = false
    private var predictiveFinishing = false
    private var predictiveJustFinished = false
    private var predictiveDirection = 1f
    private var predictiveDrawerActive = false
    private var predictiveDrawerFraction = 0f
    private var predictiveDrawerReturnAnimator: ValueAnimator? = null
    private var predictiveSubpageView: View? = null
    private var predictiveParentView: View? = null
    private var predictiveCornerRadius = 0f
    private val predictiveParentStartScale = 0.95f
    private val predictiveParentStartAlpha = 0.7f
    private val predictiveMaxCornerPx
        get() = 28f * resources.displayMetrics.density
    private val predictiveOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, predictiveCornerRadius)
        }
    }

    private fun shouldPredictSubpageBack(): Boolean {
        if (predictiveFinishing) {
            return false
        }
        val isSettingsSubpage = navTarget in setOf(
            NavTarget.SETTINGS_THEME,
            NavTarget.SETTINGS_REGISTER,
            NavTarget.SETTINGS_SYNC,
            NavTarget.SETTINGS_ABOUT,
        )
        val isMessagesSubpage = navTarget in setOf(
            NavTarget.MESSAGE,
            NavTarget.MESSAGE_COMPOSE,
        )
        if (!isSettingsSubpage && !isMessagesSubpage) {
            return false
        }
        // drawer and bottom sheet handle back themselves (they close instead)
        if (drawer.isOpen || bottomSheet.isOpen) {
            return false
        }
        val currentFrag = fragmentManager.findFragmentById(R.id.fragment)
        if (currentFrag is MessagesComposeFragment && currentFrag.hasUnsavedChanges()) {
            return false
        }
        return true
    }

    private fun resetFragmentTransform(view: View?) {
        view?.translationX = 0f
        view?.scaleX = 1f
        view?.scaleY = 1f
        view?.alpha = 1f
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // go through the dispatcher, so that fragments' callbacks can intercept it
        onBackPressedDispatcher.onBackPressed()
    }

    fun error(error: ApiError) = errorSnackbar.addError(error).show()
    fun snackbar(
        text: String,
        actionText: String? = null,
        onClick: (() -> Unit)? = null,
    ) = mainSnackbar.snackbar(text, actionText, onClick)

    fun snackbarDismiss() = mainSnackbar.dismiss()
}
