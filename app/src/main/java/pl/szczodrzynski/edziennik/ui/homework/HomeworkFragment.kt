/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-30.
 */

package pl.szczodrzynski.edziennik.ui.homework

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.google.android.material.shape.MaterialShapeDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.szkolny.font.SzkolnyFont
import kotlinx.coroutines.CoroutineScope
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Event
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.databinding.FragmentHomeworkBinding
import pl.szczodrzynski.edziennik.ext.Bundle
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ext.enableExpressiveShapeMorph
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.fragment.PagerFragment
import pl.szczodrzynski.edziennik.ui.base.fragment.setupFabHideOnScroll
import pl.szczodrzynski.edziennik.ui.event.EventDetailsBinder
import pl.szczodrzynski.edziennik.ui.event.EventDetailsDialog
import pl.szczodrzynski.edziennik.ui.event.EventManualDialog
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.IBottomSheetItem

class HomeworkFragment : PagerFragment<FragmentHomeworkBinding, MainActivity>(
    inflater = FragmentHomeworkBinding::inflate,
), CoroutineScope {

    var selectedEventId: Long? = null
        private set
    var selectedEvent: EventFull? = null
        private set
    private var activeDialog: EventDetailsDialog? = null
    private var detailBinder: EventDetailsBinder? = null

    val isTwoColumnMode: Boolean
        get() = isAdded &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            app.profile.config.ui.homeworkTwoColumnLandscape

    private val detailBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeDetailEvent()
        }
    }

    override fun getFab() = R.string.add to CommunityMaterial.Icon3.cmd_plus
    override fun getMarkAsReadType() = MetadataType.HOMEWORK

    override fun getBottomSheetItems(): List<IBottomSheetItem<*>> {
        val items = mutableListOf<IBottomSheetItem<*>>()
        items += BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_add_event)
            .withDescription(R.string.menu_add_event_desc)
            .withIcon(SzkolnyFont.Icon.szf_calendar_plus_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                EventManualDialog(activity, App.profileId, defaultType = Event.TYPE_HOMEWORK).show()
            }

        items += BottomSheetPrimaryItem(true)
            .withTitle(R.string.homework_config_two_column_landscape)
            .withDescription(R.string.homework_config_two_column_landscape_hint)
            .withIcon(CommunityMaterial.Icon2.cmd_format_columns)
            .withSwitch(app.profile.config.ui.homeworkTwoColumnLandscape) { isChecked ->
                app.profile.config.ui.homeworkTwoColumnLandscape = isChecked
                updateTwoColumnLayout()
            }
        return items
    }

    override fun getTabLayout() = b.tabLayout
    override fun getViewPager() = b.viewPager
    override suspend fun onCreatePages() = listOf(
        HomeworkListFragment().apply {
            arguments = Bundle("homeworkDate" to HomeworkDate.CURRENT)
        } to getString(R.string.homework_tab_current),
        HomeworkListFragment().apply {
            arguments = Bundle("homeworkDate" to HomeworkDate.PAST)
        } to getString(R.string.homework_tab_past)
    )

    override suspend fun onFabClick() {
        EventManualDialog(activity, App.profileId, defaultType = Event.TYPE_HOMEWORK).show()
    }

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        super.onViewReady(savedInstanceState)
        activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, detailBackCallback)

        val barColor = R.attr.colorSurface.resolveAttr(activity)
        b.detailToolbar.background = MaterialShapeDrawable().apply {
            fillColor = ColorStateList.valueOf(barColor)
        }

        b.detailCloseButton.icon = CommunityMaterial.Icon.cmd_close.toDrawable(
            activity,
            colorAttr = R.attr.colorOnSecondaryContainer,
        )
        b.detailCloseButton.enableExpressiveShapeMorph()
        b.detailCloseButton.setOnClickListener {
            closeDetailEvent()
        }

        b.detailEditButton.icon = CommunityMaterial.Icon3.cmd_pencil_outline.toDrawable(
            activity,
            colorAttr = R.attr.colorOnSecondaryContainer,
        )
        b.detailEditButton.enableExpressiveShapeMorph()
        b.detailEditButton.setOnClickListener {
            val ev = selectedEvent ?: return@setOnClickListener
            EventManualDialog(
                activity,
                ev.profileId,
                editingEvent = ev,
                onSaveListener = {
                    if (it == null) {
                        closeDetailEvent()
                    }
                },
            ).show()
        }

        detailBinder = EventDetailsBinder(
            activity = activity,
            b = b.eventDetails,
            showNotes = true,
            onClose = ::closeDetailEvent,
            onEventUpdated = { updated ->
                selectedEvent = updated
                b.detailEditButton.isVisible = updated.addedManually
            },
        )

        if (savedInstanceState != null && savedInstanceState.containsKey("selectedEventId")) {
            selectedEventId = savedInstanceState.getLong("selectedEventId")
        }

        (b.eventDetails.root as? NestedScrollView)?.setupFabHideOnScroll(
            isEnabled = { isTwoColumnMode },
            onHide = ::hideFab,
            onShow = ::showFab,
        )

        updateTwoColumnLayout()
    }

    fun openEventInDetail(event: EventFull) {
        if (!isAdded || view == null) return
        activity.hideKeyboard()
        selectedEventId = event.id
        selectedEvent = event
        b.emptyDetailLayout.isVisible = false
        b.detailContainer.isVisible = true
        b.detailEditButton.isVisible = event.addedManually

        detailBinder?.bind(event)
        showFab()
        (b.eventDetails.root as? NestedScrollView)?.scrollTo(0, 0)
        detailBackCallback.isEnabled = isTwoColumnMode && !isHidden
        childFragmentManager.fragments.filterIsInstance<HomeworkListFragment>().forEach {
            it.setActiveEventId(event.id)
        }
    }

    fun closeDetailEvent() {
        if (!isAdded || view == null) return
        selectedEventId = null
        selectedEvent = null
        showFab()
        detailBinder?.unbind()
        b.detailContainer.isVisible = false
        b.emptyDetailLayout.isVisible = true
        detailBackCallback.isEnabled = false
        childFragmentManager.fragments.filterIsInstance<HomeworkListFragment>().forEach {
            it.setActiveEventId(null)
        }
    }

    fun showEventDetailsDialog(event: EventFull) {
        val dialog = EventDetailsDialog(
            activity = activity,
            event = event,
            showNotes = true,
            onDismiss = {
                activeDialog = null
            },
        )
        activeDialog = dialog
        dialog.show()
    }

    fun updateTwoColumnLayout() {
        if (!isAdded || view == null) return
        val twoCol = isTwoColumnMode

        b.detailDivider.isVisible = twoCol
        b.detailColumn.isVisible = twoCol

        val listLp = b.listColumn.layoutParams as LinearLayout.LayoutParams
        listLp.weight = if (twoCol) 0.42f else 1f
        listLp.width = if (twoCol) 0 else ViewGroup.LayoutParams.MATCH_PARENT
        b.listColumn.layoutParams = listLp

        childFragmentManager.fragments.filterIsInstance<HomeworkListFragment>().forEach {
            it.setActiveEventId(if (twoCol) selectedEventId else null)
        }

        if (twoCol) {
            val hasDetail = selectedEvent != null
            b.detailContainer.isVisible = hasDetail
            b.emptyDetailLayout.isVisible = !hasDetail
            if (hasDetail) {
                openEventInDetail(selectedEvent!!)
            }
        } else {
            val openEv = selectedEvent
            if (openEv != null) {
                closeDetailEvent()
                if (!isHidden && activity.navTarget == NavTarget.HOMEWORK) {
                    showEventDetailsDialog(openEv)
                }
            }
        }

        showFab()
        detailBackCallback.isEnabled = twoCol && selectedEventId != null && !isHidden
    }

    override suspend fun onPageSelected(position: Int) {
        super.onPageSelected(position)
        showFab()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && isTwoColumnMode) {
            activeDialog?.let { dialog ->
                val ev = dialog.event
                dialog.dismiss()
                activeDialog = null
                openEventInDetail(ev)
            }
        }
        updateTwoColumnLayout()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        detailBackCallback.isEnabled = isTwoColumnMode && selectedEventId != null && !hidden
        if (!hidden && isAdded && view != null) {
            updateTwoColumnLayout()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedEventId?.let { outState.putLong("selectedEventId", it) }
    }
}
