/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-27.
 */

package pl.szczodrzynski.edziennik.ui.notes

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Note
import pl.szczodrzynski.edziennik.data.db.entity.Noteable
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.databinding.NotesFragmentBinding
import pl.szczodrzynski.edziennik.ext.enableExpressiveShapeMorph
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.base.fragment.setupFabHideOnScroll
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.IBottomSheetItem

class NotesFragment : BaseFragment<NotesFragmentBinding, MainActivity>(
    inflater = NotesFragmentBinding::inflate,
) {

    override fun getScrollingView() = b.list
    override fun getFab() =
        R.string.notes_action_add to CommunityMaterial.Icon3.cmd_text_box_plus_outline

    private val manager
        get() = app.noteManager

    private var adapter: NoteListAdapter? = null
    var selectedNoteId: Long? = null
        private set
    var selectedNote: Note? = null
        private set
    var selectedOwner: Noteable? = null
        private set
    private var activeDialog: NoteDetailsDialog? = null
    private var detailBinder: NoteDetailsBinder? = null

    val isTwoColumnMode: Boolean
        get() = isAdded &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            app.profile.config.ui.notesTwoColumnLandscape

    private val detailBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeDetailNote()
        }
    }

    override fun getBottomSheetItems(): List<IBottomSheetItem<*>> {
        val items = mutableListOf<IBottomSheetItem<*>>()
        items += BottomSheetPrimaryItem(true)
            .withTitle(R.string.notes_config_two_column_landscape)
            .withDescription(R.string.notes_config_two_column_landscape_hint)
            .withIcon(CommunityMaterial.Icon2.cmd_format_columns)
            .withSwitch(app.profile.config.ui.notesTwoColumnLandscape) { isChecked ->
                app.profile.config.ui.notesTwoColumnLandscape = isChecked
                updateTwoColumnLayout()
            }
        return items
    }

    private fun updateDetailToolbarColor(note: Note?) {
        val barColor = R.attr.colorSurface.resolveAttr(activity)
        val targetColor = if (note?.color != null) {
            MaterialColors.layer(barColor, note.color.toInt(), 0x50 / 255f)
        } else {
            barColor
        }
        val bg = b.detailToolbar.background as? MaterialShapeDrawable
        if (bg != null) {
            bg.fillColor = ColorStateList.valueOf(targetColor)
        } else {
            b.detailToolbar.background = MaterialShapeDrawable().apply {
                fillColor = ColorStateList.valueOf(targetColor)
            }
        }
    }

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, detailBackCallback)

        b.detailToolbar.background = MaterialShapeDrawable()
        updateDetailToolbarColor(selectedNote)

        b.detailCloseButton.icon = CommunityMaterial.Icon.cmd_close.toDrawable(
            activity,
            colorAttr = R.attr.colorOnSecondaryContainer,
        )
        b.detailCloseButton.enableExpressiveShapeMorph()
        b.detailCloseButton.setOnClickListener {
            closeDetailNote()
        }

        b.detailEditButton.icon = CommunityMaterial.Icon3.cmd_pencil_outline.toDrawable(
            activity,
            colorAttr = R.attr.colorOnSecondaryContainer,
        )
        b.detailEditButton.enableExpressiveShapeMorph()
        b.detailEditButton.setOnClickListener {
            val note = selectedNote ?: return@setOnClickListener
            launch {
                val owner = selectedOwner ?: withContext(Dispatchers.IO) {
                    manager.getOwner(note)
                } as? Noteable

                NoteEditorDialog(
                    activity = activity,
                    owner = owner,
                    editingNote = note,
                    profileId = App.profileId,
                ).show()
            }
        }

        detailBinder = NoteDetailsBinder(
            activity = activity,
            b = b.noteDetails,
            onClose = ::closeDetailNote,
            onNoteUpdated = { updated ->
                selectedNote = updated
                b.detailEditButton.isVisible = updated.canEdit
                updateDetailToolbarColor(updated)
            },
        )
        b.noteDetails.subtitle.subtitleText.isVisible = false

        b.noteDetails.root.setupFabHideOnScroll(
            isEnabled = { isTwoColumnMode },
            onHide = ::hideFab,
            onShow = ::showFab,
        )

        if (savedInstanceState != null && savedInstanceState.containsKey("selectedNoteId")) {
            selectedNoteId = savedInstanceState.getLong("selectedNoteId")
        }

        val adapter = NoteListAdapter(
            activity = activity,
            onNoteClick = this::onNoteClick,
            onNoteEditClick = this::onNoteEditClick,
        )
        this.adapter = adapter
        adapter.activeNoteId = if (isTwoColumnMode) selectedNoteId else null

        app.db.noteDao().getAll(profileId = App.profileId).observe(activity) { allNotes ->
            if (!isAdded) return@observe

            // show/hide relevant views
            b.progressBar.isVisible = false
            b.list.isVisible = allNotes.isNotEmpty()
            b.noData.isVisible = allNotes.isEmpty()
            if (allNotes.isEmpty()) {
                return@observe
            }

            val notes = allNotes.groupBy { it.ownerType }.flatMap { (ownerType, notes) ->
                if (ownerType != null) {
                    // construct a dummy note, used as the category separator
                    val categoryItem = Note(
                        profileId = 0,
                        id = 0,
                        ownerType = ownerType,
                        ownerId = 0,
                        topic = null,
                        body = "",
                        color = null,
                    )
                    categoryItem.isCategoryItem = true
                    val mutableNotes = notes.toMutableList()
                    mutableNotes.add(0, categoryItem)
                    return@flatMap mutableNotes
                }
                return@flatMap notes
            }

            // apply the new note list
            adapter.setAllItems(notes, addSearchField = true)

            // configure the adapter & recycler view
            if (b.list.adapter == null) {
                b.list.adapter = adapter
                b.list.apply {
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(context)
                }
            }

            // reapply the filter
            adapter.getSearchField()?.applyTo(adapter)

            if (selectedNoteId != null) {
                val found = allNotes.firstOrNull { it.id == selectedNoteId }
                if (found != null) {
                    selectedNote = found
                    updateDetailToolbarColor(found)
                    if (isTwoColumnMode) {
                        launch {
                            val owner = withContext(Dispatchers.IO) {
                                manager.getOwner(found)
                            } as? Noteable
                            selectedOwner = owner
                            openNoteInDetail(owner, found)
                        }
                    }
                } else if (isTwoColumnMode) {
                    closeDetailNote()
                }
            }
        }

        updateTwoColumnLayout()
    }

    private fun onNoteClick(note: Note) = launch {
        activity.hideKeyboard()
        val owner = withContext(Dispatchers.IO) {
            manager.getOwner(note)
        } as? Noteable

        if (isTwoColumnMode) {
            openNoteInDetail(owner, note)
        } else {
            showNoteDetailsDialog(owner, note)
        }
    }

    private fun onNoteEditClick(note: Note) = launch {
        val owner = withContext(Dispatchers.IO) {
            manager.getOwner(note)
        } as? Noteable

        NoteEditorDialog(
            activity = activity,
            owner = owner,
            editingNote = note,
            profileId = App.profileId,
        ).show()
    }

    fun openNoteInDetail(owner: Noteable?, note: Note) {
        if (!isAdded || view == null) return
        activity.hideKeyboard()
        selectedNoteId = note.id
        selectedNote = note
        selectedOwner = owner
        b.emptyDetailLayout.isVisible = false
        b.detailContainer.isVisible = true
        b.detailEditButton.isVisible = note.canEdit
        updateDetailToolbarColor(note)

        detailBinder?.bind(owner, note)
        b.noteDetails.subtitle.subtitleText.isVisible = false
        showFab()
        (b.noteDetails.root as? NestedScrollView)?.scrollTo(0, 0)
        adapter?.activeNoteId = note.id
        detailBackCallback.isEnabled = isTwoColumnMode && !isHidden
    }

    fun closeDetailNote() {
        if (!isAdded || view == null) return
        selectedNoteId = null
        selectedNote = null
        selectedOwner = null
        updateDetailToolbarColor(null)
        showFab()
        detailBinder?.unbind()
        b.detailContainer.isVisible = false
        b.emptyDetailLayout.isVisible = true
        detailBackCallback.isEnabled = false
        adapter?.activeNoteId = null
    }

    fun showNoteDetailsDialog(owner: Noteable?, note: Note) {
        val dialog = NoteDetailsDialog(
            activity = activity,
            owner = owner,
            note = note,
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

        adapter?.activeNoteId = if (twoCol) selectedNoteId else null

        if (twoCol) {
            val hasDetail = selectedNote != null
            b.detailContainer.isVisible = hasDetail
            b.emptyDetailLayout.isVisible = !hasDetail
            if (hasDetail) {
                openNoteInDetail(selectedOwner, selectedNote!!)
            }
        } else {
            val openNote = selectedNote
            val openOwner = selectedOwner
            if (openNote != null) {
                closeDetailNote()
                if (!isHidden && activity.navTarget == NavTarget.NOTES) {
                    showNoteDetailsDialog(openOwner, openNote)
                }
            }
        }

        showFab()
        detailBackCallback.isEnabled = twoCol && selectedNoteId != null && !isHidden
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && isTwoColumnMode) {
            activeDialog?.let { dialog ->
                val n = dialog.note
                val o = dialog.owner
                dialog.dismiss()
                activeDialog = null
                openNoteInDetail(o, n)
            }
        }
        updateTwoColumnLayout()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        detailBackCallback.isEnabled = isTwoColumnMode && selectedNoteId != null && !hidden
        if (!hidden && isAdded && view != null) {
            updateTwoColumnLayout()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedNoteId?.let { outState.putLong("selectedNoteId", it) }
    }

    override suspend fun onFabClick() {
        NoteEditorDialog(
            activity = activity,
            owner = null,
            editingNote = null,
            profileId = App.profileId,
        ).show()
    }
}
