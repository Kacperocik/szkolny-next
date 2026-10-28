/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-4.
 */

package pl.szczodrzynski.edziennik.ui.messages.list

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Message
import pl.szczodrzynski.edziennik.data.db.entity.Teacher
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.databinding.MessagesListFragmentBinding
import pl.szczodrzynski.edziennik.ext.Bundle
import pl.szczodrzynski.edziennik.ext.attachToastHint
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ext.getInt
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.plural
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.utils.SimpleDividerItemDecoration

class MessagesListFragment : BaseFragment<MessagesListFragmentBinding, MainActivity>(
    inflater = MessagesListFragmentBinding::inflate,
) {

    override fun getScrollingView() = b.list
    override fun getSyncParams() = when (messageType) {
        Message.TYPE_RECEIVED -> FeatureType.MESSAGES_INBOX to null
        Message.TYPE_SENT -> FeatureType.MESSAGES_SENT to null
        else -> null
    }

    private lateinit var adapter: MessagesAdapter
    private val manager
        get() = app.messageManager
    var teachers = listOf<Teacher>()

    private val messageType by lazy {
        arguments.getInt("messageType", Message.TYPE_RECEIVED)
    }

    @SuppressLint("RestrictedApi")
    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        val (emptyText, emptyIllustration) = when (messageType) {
            Message.TYPE_SENT -> R.string.messages_no_sent to R.drawable.illustration_messages_sent
            Message.TYPE_DELETED -> R.string.messages_no_trash to R.drawable.illustration_messages_trash
            Message.TYPE_DRAFT -> R.string.messages_no_drafts to R.drawable.illustration_messages_drafts
            else -> R.string.messages_no_received to R.drawable.illustration_messages_received
        }
        b.noData.setText(emptyText)
        b.noData.setCompoundDrawablesWithIntrinsicBounds(0, emptyIllustration, 0, 0)

        var recyclerViewState =
            savedInstanceState?.getParcelable<LinearLayoutManager.SavedState>("recyclerViewState")
        val searchText = savedInstanceState?.getString("searchText")

        teachers = withContext(Dispatchers.Default) {
            app.db.teacherDao().getAllNow(App.profileId)
        }

        adapter = MessagesAdapter(activity, teachers, onMessageClick = {
            activity.hideKeyboard()
            if (it.isDraft) {
                activity.navigate(
                    navTarget = NavTarget.MESSAGE_COMPOSE,
                    args = Bundle("message" to app.gson.toJson(it)),
                )
            } else {
                val parent = parentFragment as? MessagesFragment
                if (parent?.isTwoColumnMode == true) {
                    parent.openMessageInDetail(it.id)
                } else {
                    activity.navigate(
                        navTarget = NavTarget.MESSAGE,
                        args = Bundle("messageId" to it.id),
                    )
                }
            }
        }, onStarClick = {
            this@MessagesListFragment.launch {
                val newStarred = !it.isStarred
                manager.starMessage(it, newStarred)
                (parentFragment as? MessagesFragment)?.onMessageStarChanged(it.id, newStarred)
            }
        }, onSelectionChanged = ::onSelectionChanged)
        adapter.activeMessageId = (parentFragment as? MessagesFragment)
            ?.takeIf { it.isTwoColumnMode }
            ?.selectedMessageId
        setupSelectionBar()

        app.db.messageDao().getAllByType(App.profileId, messageType).observe(viewLifecycleOwner, Observer { messages ->
            if (!isAdded || !this@MessagesListFragment::adapter.isInitialized)
                return@Observer

            messages.forEach { message ->
                message.filterNotes()

                // uh oh, so these are the workarounds ??
                message.recipients?.removeAll { it.profileId != message.profileId }
                message.recipients?.forEach { recipient ->
                    if (recipient.fullName == null) {
                        recipient.fullName = teachers.firstOrNull { it.id == recipient.id }?.fullName ?: ""
                    }
                }
            }

            // show/hide relevant views
            b.progressBar.isVisible = false
            b.list.isVisible = messages.isNotEmpty()
            b.noData.isVisible = messages.isEmpty()
            if (messages.isEmpty()) {
                return@Observer
            }

            // apply the new message list
            adapter.setAllItems(messages, searchText, addSearchField = true)

            // configure the adapter & recycler view
            if (b.list.adapter == null) {
                b.list.apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(context)
                    this.adapter = this@MessagesListFragment.adapter
                }
            }

            val layoutManager = (b.list.layoutManager as? LinearLayoutManager) ?: return@Observer
            val currentScrollState = recyclerViewState ?: layoutManager.onSaveInstanceState()
            recyclerViewState = null

            // reapply the filter
            adapter.getSearchField()?.applyTo(adapter) {
                // restore the previously saved scroll position
                currentScrollState?.let {
                    layoutManager.onRestoreInstanceState(it)
                }
                // restored scroll position above
                if (adapter.isSelectionMode) {
                    onSelectionChanged(adapter.selectedCount)
                }
            }
        })
    }

    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            adapter.clearSelection()
        }
    }

    private fun setupSelectionBar() {
        b.selectionBar.background = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(28.dp.toFloat())
                .build()
        ).apply {
            fillColor = ColorStateList.valueOf(R.attr.colorSecondaryContainer.resolveAttr(activity))
        }
        val iconColor = R.attr.colorOnSecondaryContainer
        b.selectionClose.icon = CommunityMaterial.Icon.cmd_close.toDrawable(activity, colorAttr = iconColor)
        b.selectionSelectAll.icon = CommunityMaterial.Icon.cmd_checkbox_multiple_marked_outline.toDrawable(activity, colorAttr = iconColor)
        b.selectionDelete.icon = CommunityMaterial.Icon3.cmd_trash_can_outline.toDrawable(activity, colorAttr = iconColor)
        b.selectionRestore.icon = CommunityMaterial.Icon.cmd_delete_restore.toDrawable(activity, colorAttr = iconColor)
        b.selectionClose.onClick { adapter.clearSelection() }
        b.selectionSelectAll.onClick { adapter.selectAll() }
        b.selectionDelete.onClick { deleteSelected() }
        b.selectionRestore.onClick { restoreSelected() }

        b.selectionClose.attachToastHint(R.string.messages_selection_close)
        b.selectionSelectAll.attachToastHint(R.string.messages_select_all)
        b.selectionDelete.attachToastHint(R.string.message_delete)
        b.selectionRestore.attachToastHint(R.string.messages_restore)

        // deleted messages can only be restored
        val isDeletedList = messageType == Message.TYPE_DELETED
        b.selectionDelete.isVisible = !isDeletedList
        b.selectionRestore.isVisible = isDeletedList

        activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, selectionBackCallback)
    }

    private fun onSelectionChanged(count: Int) {
        val isActive = count > 0
        selectionBackCallback.isEnabled = isActive
        if (isActive) {
            b.selectionCount.text = activity.plural(R.plurals.messages_selected, count)
            b.selectionSelectAll.isEnabled = !adapter.isAllSelected
        }
        if (b.selectionBar.isVisible != isActive) {
            b.selectionBar.isVisible = isActive
        }
    }

    private fun deleteSelected() {
        val selected = adapter.getSelectedMessages()
        if (selected.isEmpty())
            return
        val isDraft = messageType == Message.TYPE_DRAFT
        SimpleDialog<Unit>(activity) {
            title(activity.plural(R.plurals.messages_delete_selected_confirmation, selected.size))
            message(
                if (isDraft) R.string.messages_delete_selected_drafts_confirmation_text
                else R.string.messages_delete_selected_confirmation_text
            )
            positive(R.string.ok) {
                val parent = parentFragment as? MessagesFragment
                for (message in selected) {
                    if (isDraft)
                        manager.deleteDraft(message.profileId, message.id)
                    else
                        manager.markAsDeleted(message)
                    if (parent?.selectedMessageId == message.id) {
                        parent.closeDetailMessage()
                    }
                }
                if (isAdded)
                    adapter.clearSelection()
                Toast.makeText(
                    activity,
                    activity.plural(R.plurals.messages_deleted, selected.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
            negative(R.string.cancel)
        }.show()
    }

    private fun restoreSelected() {
        val selected = adapter.getSelectedMessages()
        if (selected.isEmpty())
            return
        launch {
            val parent = parentFragment as? MessagesFragment
            for (message in selected) {
                manager.restore(message)
                if (parent?.selectedMessageId == message.id) {
                    parent.closeDetailMessage()
                }
            }
            if (isAdded)
                adapter.clearSelection()
            Toast.makeText(
                activity,
                activity.plural(R.plurals.messages_restored, selected.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setActiveMessageId(messageId: Long?) {
        if (this::adapter.isInitialized) {
            adapter.setActiveMessage(messageId)
        }
    }

    override fun onPause() {
        super.onPause()
        // leaving the page (e.g. switching tabs) ends the selection
        if (this::adapter.isInitialized)
            adapter.clearSelection()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden && this::adapter.isInitialized)
            adapter.clearSelection()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!isAdded || !this::adapter.isInitialized)
            return
        val layoutManager = (b.list.layoutManager as? LinearLayoutManager)
        val searchField = adapter.getSearchField()
        outState.putParcelable("recyclerViewState", layoutManager?.onSaveInstanceState())
        outState.putString("searchText", searchField?.searchText?.toString())
    }
}
