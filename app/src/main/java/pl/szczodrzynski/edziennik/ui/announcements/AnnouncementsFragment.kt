package pl.szczodrzynski.edziennik.ui.announcements

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.shape.MaterialShapeDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.AnnouncementGetEvent
import pl.szczodrzynski.edziennik.data.db.full.AnnouncementFull
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.databinding.FragmentAnnouncementsBinding
import pl.szczodrzynski.edziennik.ext.copyToClipboard
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ext.enableExpressiveShapeMorph
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.messages.MessagesUtils
import pl.szczodrzynski.edziennik.ui.notes.setupNotesButton
import pl.szczodrzynski.edziennik.utils.BetterLink
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetGroup
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.IBottomSheetItem
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph

class AnnouncementsFragment : BaseFragment<FragmentAnnouncementsBinding, MainActivity>(
    inflater = FragmentAnnouncementsBinding::inflate,
) {

    override fun getScrollingView() = b.announcementsView
    override fun getSyncParams() = FeatureType.ANNOUNCEMENTS to null

    var selectedAnnouncementId: Long? = null
        private set
    private var selectedAnnouncement: AnnouncementFull? = null
    private var activeDialog: AnnouncementDetailsDialog? = null

    val isTwoColumnMode: Boolean
        get() = isAdded &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            app.profile.config.ui.announcementsTwoColumnLandscape

    private val detailBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeDetailAnnouncement()
        }
    }

    override fun getBottomSheetItems(): List<IBottomSheetItem<*>> {
        val items = mutableListOf<IBottomSheetItem<*>>()
        items += BottomSheetPrimaryItem(true)
            .withTitle(R.string.announcements_config_two_column_landscape)
            .withDescription(R.string.announcements_config_two_column_landscape_hint)
            .withIcon(CommunityMaterial.Icon2.cmd_format_columns)
            .withSwitch(app.profile.config.ui.announcementsTwoColumnLandscape) { isChecked ->
                app.profile.config.ui.announcementsTwoColumnLandscape = isChecked
                updateTwoColumnLayout()
            }

        items += BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_mark_as_read)
            .withIcon(CommunityMaterial.Icon.cmd_eye_check_outline)
            .withGroup(BottomSheetGroup.ACTIONS)
            .withOnClickListener {
                activity.bottomSheet.close()
                if (app.profile.loginStoreType == LoginType.LIBRUS) {
                    EdziennikTask.announcementsRead(App.profileId).enqueue(requireContext())
                } else {
                    launch(Dispatchers.IO) {
                        app.db.metadataDao().setAllSeen(App.profileId, MetadataType.ANNOUNCEMENT, true)
                    }
                    Toast.makeText(activity, R.string.main_menu_mark_as_read_success, Toast.LENGTH_SHORT).show()
                }
            }
        return items
    }

    private var adapter: AnnouncementsAdapter? = null

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
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
            closeDetailAnnouncement()
        }

        if (savedInstanceState != null && savedInstanceState.containsKey("selectedAnnouncementId")) {
            selectedAnnouncementId = savedInstanceState.getLong("selectedAnnouncementId")
        }

        adapter = AnnouncementsAdapter(activity) { _, announcement ->
            activity.hideKeyboard()
            if (isTwoColumnMode) {
                openAnnouncementInDetail(announcement)
            } else {
                if (announcement.text == null || (app.profile.loginStoreType == LoginType.LIBRUS && !announcement.seen)) {
                    EdziennikTask.announcementGet(App.profileId, announcement).enqueue(requireContext())
                } else {
                    showAnnouncementDetailsDialog(announcement)
                }
            }
        }

        adapter?.activeAnnouncementId = if (isTwoColumnMode) selectedAnnouncementId else null

        b.announcementsView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = this@AnnouncementsFragment.adapter
        }

        app.db.announcementDao().getAll(App.profileId).observe(viewLifecycleOwner) { announcements ->
            if (!isAdded) return@observe

            for (it in announcements) {
                it.filterNotes()
            }

            if (announcements.isNullOrEmpty()) {
                b.announcementsView.isVisible = false
                b.announcementsNoData.isVisible = true
            } else {
                adapter?.setAllItems(announcements, null, true)
                adapter?.filter?.filter("")
                b.announcementsView.isVisible = true
                b.announcementsNoData.isVisible = false
            }

            if (selectedAnnouncementId != null) {
                val found = announcements.firstOrNull { it.id == selectedAnnouncementId }
                if (found != null) {
                    selectedAnnouncement = found
                    if (isTwoColumnMode) {
                        bindAnnouncementDetails(found)
                    }
                } else if (isTwoColumnMode) {
                    closeDetailAnnouncement()
                }
            }
        }

        updateTwoColumnLayout()
    }

    fun openAnnouncementInDetail(announcement: AnnouncementFull) {
        if (!isAdded || view == null) return
        activity.hideKeyboard()
        selectedAnnouncementId = announcement.id
        selectedAnnouncement = announcement
        b.emptyDetailLayout.isVisible = false
        b.detailContainer.isVisible = true
        adapter?.setActiveAnnouncement(announcement.id)
        detailBackCallback.isEnabled = isTwoColumnMode && !isHidden

        if (announcement.text == null || (app.profile.loginStoreType == LoginType.LIBRUS && !announcement.seen)) {
            b.detailSubject.text = announcement.subject
            b.detailProgressBar.isVisible = true
            b.detailScrollView.isVisible = false
            EdziennikTask.announcementGet(App.profileId, announcement).enqueue(requireContext())
        } else {
            bindAnnouncementDetails(announcement)
        }
    }

    private fun bindAnnouncementDetails(announcement: AnnouncementFull) {
        if (!isAdded || view == null) return
        b.detailProgressBar.isVisible = false
        b.detailScrollView.isVisible = true

        b.detailSubject.text = announcement.subject
        b.detailSubject.setOnClickListener {
            b.detailSubject.maxLines = if (b.detailSubject.maxLines >= 30) 2 else 30
        }
        b.detailSubject.setOnLongClickListener {
            val subject = announcement.subject
            if (subject.isNotBlank()) {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                subject.copyToClipboard(activity)
                Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                true
            } else false
        }

        b.detailSenderName.text = announcement.teacherName
        b.detailSenderImage.setImageBitmap(
            MessagesUtils.getProfileImage(48, 24, 18, 12, 1, announcement.teacherName)
        )
        b.detailSenderCard.enableExpressiveCardShapeMorph(null, 250L, 250L, 200L)
        b.detailSenderImage.enableExpressiveImageShapeMorph(10f, 250L, 250L, 200L)

        val teacherName = announcement.teacherName ?: ""
        b.detailSenderCard.setOnClickListener { v ->
            BetterLink.showTeacherMenu(
                view = v,
                teacherId = announcement.teacherId,
                teacherName = teacherName,
                onActionSelected = null,
            )
        }
        b.detailSenderCard.setOnLongClickListener {
            if (teacherName.isNotBlank()) {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                teacherName.copyToClipboard(activity)
                Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                true
            } else false
        }

        val start = announcement.startDate?.formattedString ?: "-"
        val end = announcement.endDate?.let { " do ${it.formattedString}" } ?: ""
        b.detailDateRange.text = "$start$end"
        b.detailText.text = announcement.text
        b.detailNotesButton.setupNotesButton(activity, announcement)

        if (!announcement.seen && app.profile.loginStoreType != LoginType.LIBRUS) {
            announcement.seen = true
            launch(Dispatchers.IO) {
                app.db.metadataDao().setSeen(app.profile.id, announcement, true)
            }
            adapter?.notifyDataSetChanged()
        }
    }

    fun closeDetailAnnouncement() {
        selectedAnnouncementId = null
        selectedAnnouncement = null
        if (!isAdded || view == null) return
        b.detailContainer.isVisible = false
        b.emptyDetailLayout.isVisible = true
        detailBackCallback.isEnabled = false
        adapter?.setActiveAnnouncement(null)
    }

    fun updateTwoColumnLayout() {
        if (!isAdded || view == null) return
        val twoCol = isTwoColumnMode
        b.detailDivider.isVisible = twoCol
        b.detailColumn.isVisible = twoCol
        if (twoCol) {
            val hasDetail = selectedAnnouncementId != null
            b.emptyDetailLayout.isVisible = !hasDetail
            b.detailContainer.isVisible = hasDetail
            if (hasDetail && selectedAnnouncement != null) {
                bindAnnouncementDetails(selectedAnnouncement!!)
            }
        } else {
            val openAnn = selectedAnnouncement
            if (openAnn != null) {
                closeDetailAnnouncement()
                if (!isHidden && activity.navTarget == NavTarget.ANNOUNCEMENTS) {
                    showAnnouncementDetailsDialog(openAnn)
                }
            }
        }
        detailBackCallback.isEnabled = twoCol && selectedAnnouncementId != null && !isHidden
        adapter?.setActiveAnnouncement(if (twoCol) selectedAnnouncementId else null)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onAnnouncementGetEvent(event: AnnouncementGetEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        if (isTwoColumnMode && selectedAnnouncementId == event.announcement.id) {
            selectedAnnouncement = event.announcement
            bindAnnouncementDetails(event.announcement)
        } else if (!isTwoColumnMode) {
            showAnnouncementDetailsDialog(event.announcement)
        }
    }

    private fun showAnnouncementDetailsDialog(announcement: AnnouncementFull) {
        val dialog = AnnouncementDetailsDialog(
            activity = activity,
            announcement = announcement,
            onSeen = {
                adapter?.notifyDataSetChanged()
            },
            onDismiss = {
                activeDialog = null
            },
        )
        activeDialog = dialog
        dialog.show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && isTwoColumnMode) {
            activeDialog?.let { dialog ->
                val ann = dialog.announcement
                dialog.dismiss()
                activeDialog = null
                openAnnouncementInDetail(ann)
            }
        }
        updateTwoColumnLayout()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        detailBackCallback.isEnabled = isTwoColumnMode && selectedAnnouncementId != null && !hidden
        if (!hidden && isAdded && view != null) {
            updateTwoColumnLayout()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedAnnouncementId?.let { outState.putLong("selectedAnnouncementId", it) }
    }
}
