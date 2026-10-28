package pl.szczodrzynski.edziennik.ui.messages.list

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Message
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.databinding.MessagesFragmentBinding
import pl.szczodrzynski.edziennik.ext.Bundle
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ui.base.fragment.PagerFragment
import pl.szczodrzynski.edziennik.ui.dialogs.settings.MessagesConfigDialog
import pl.szczodrzynski.edziennik.ui.messages.single.MessageFragment
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem

class MessagesFragment : PagerFragment<MessagesFragmentBinding, MainActivity>(
    inflater = MessagesFragmentBinding::inflate,
) {
    companion object {
        var pageSelection = 0
        var pendingDetailArgs: Bundle? = null
    }

    override var savedPageSelection: Int
        get() = pageSelection
        set(value) {
            pageSelection = value
        }

    var selectedMessageId: Long? = null
        private set
    private var selectedMessageArgs: Bundle? = null

    val isTwoColumnMode: Boolean
        get() = isAdded &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            app.profile.config.ui.messagesTwoColumnLandscape

    private val detailBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeDetailMessage()
        }
    }

    override fun getFab() = R.string.compose to CommunityMaterial.Icon3.cmd_pencil_outline
    override fun getBottomSheetItems() = listOf(
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_message_compose)
            .withIcon(CommunityMaterial.Icon3.cmd_pencil_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                activity.navigate(navTarget = NavTarget.MESSAGE_COMPOSE)
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_messages_config)
            .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                MessagesConfigDialog(activity, false).show()
            }
    )

    override fun getTabLayout() = b.tabLayout
    override fun getViewPager() = b.viewPager
    override suspend fun onCreatePages() = listOf(
        MessagesListFragment().apply {
            arguments = Bundle("messageType" to Message.TYPE_RECEIVED)
        } to getString(R.string.messages_tab_received),
        MessagesListFragment().apply {
            arguments = Bundle("messageType" to Message.TYPE_SENT)
        } to getString(R.string.messages_tab_sent),
        MessagesListFragment().apply {
            arguments = Bundle("messageType" to Message.TYPE_DELETED)
        } to getString(R.string.messages_tab_deleted),
        MessagesListFragment().apply {
            arguments = Bundle("messageType" to Message.TYPE_DRAFT)
        } to getString(R.string.messages_tab_draft),
    )

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, detailBackCallback)

        if (savedInstanceState != null && savedInstanceState.containsKey("selectedMessageId")) {
            selectedMessageId = savedInstanceState.getLong("selectedMessageId")
            selectedMessageArgs = savedInstanceState.getBundle("selectedMessageArgs")
        }

        val requestedType = arguments?.getInt("messageType", -1) ?: -1
        if (requestedType != -1) {
            arguments?.remove("messageType")
            val targetIndex = when (requestedType) {
                Message.TYPE_RECEIVED -> 0
                Message.TYPE_SENT -> 1
                Message.TYPE_DELETED -> 2
                Message.TYPE_DRAFT -> 3
                else -> -1
            }
            if (targetIndex != -1) {
                pageSelection = targetIndex
            }
        }

        super.onViewReady(savedInstanceState)

        updateTwoColumnLayout()
        consumePendingDetailArgs()
    }

    fun openMessageInDetail(messageId: Long, messageArgs: Bundle? = null) {
        if (!isAdded || view == null) return
        activity.hideKeyboard()
        val args = (messageArgs?.let { Bundle(it) } ?: Bundle()).apply {
            putLong("messageId", messageId)
        }
        selectedMessageId = messageId
        selectedMessageArgs = args
        b.emptyDetailLayout.isVisible = false
        b.detailContainer.isVisible = true
        val fragment = MessageFragment().apply {
            arguments = Bundle(args)
        }
        childFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.detailContainer, fragment, "detail_message")
            .commitAllowingStateLoss()
        showFab()
        updateDetailBackCallback()
        notifyListActiveMessageChanged()
    }

    fun closeDetailMessage() {
        selectedMessageId = null
        selectedMessageArgs = null
        if (!isAdded || view == null) return
        childFragmentManager.findFragmentByTag("detail_message")?.let {
            childFragmentManager.beginTransaction()
                .remove(it)
                .commitAllowingStateLoss()
        }
        b.detailContainer.isVisible = false
        b.emptyDetailLayout.isVisible = true
        showFab()
        updateDetailBackCallback()
        notifyListActiveMessageChanged()
    }

    fun updateTwoColumnLayout() {
        if (!isAdded || view == null) return
        val twoCol = isTwoColumnMode
        b.detailDivider.isVisible = twoCol
        b.detailColumn.isVisible = twoCol
        if (twoCol) {
            val hasDetail = selectedMessageId != null
            b.emptyDetailLayout.isVisible = !hasDetail
            b.detailContainer.isVisible = hasDetail
            val existingDetail = childFragmentManager.findFragmentByTag("detail_message")
            if (hasDetail && existingDetail == null) {
                openMessageInDetail(selectedMessageId!!, selectedMessageArgs)
            }
        } else {
            val openId = selectedMessageId
            val openArgs = selectedMessageArgs
            if (openId != null) {
                closeDetailMessage()
                if (!isHidden && activity.navTarget == NavTarget.MESSAGES) {
                    activity.navigate(
                        navTarget = NavTarget.MESSAGE,
                        args = openArgs ?: Bundle("messageId" to openId),
                    )
                }
            }
        }
        updateDetailBackCallback()
        notifyListActiveMessageChanged()
        showFab()
    }

    override suspend fun onPageSelected(position: Int) {
        super.onPageSelected(position)
        showFab()
    }

    fun consumePendingDetailArgs() {
        val pending = pendingDetailArgs
        if (pending != null) {
            pendingDetailArgs = null
            handleMessageArgs(pending)
            return
        }
        val argMessageId = arguments?.getLong("messageId", -1L) ?: -1L
        if (argMessageId != -1L) {
            val args = Bundle(arguments).apply {
                putLong("messageId", argMessageId)
            }
            arguments?.remove("messageId")
            arguments?.remove("message")
            arguments?.remove("sentDate")
            handleMessageArgs(args)
        }
    }

    private fun handleMessageArgs(args: Bundle) {
        val requestedType = args.getInt("messageType", -1)
        if (requestedType != -1) {
            val targetIndex = when (requestedType) {
                Message.TYPE_RECEIVED -> 0
                Message.TYPE_SENT -> 1
                Message.TYPE_DELETED -> 2
                Message.TYPE_DRAFT -> 3
                else -> -1
            }
            if (targetIndex != -1 && b.viewPager.adapter != null && b.viewPager.currentItem != targetIndex) {
                b.viewPager.setCurrentItem(targetIndex, false)
                pageSelection = targetIndex
            }
        }
        val messageId = args.getLong("messageId", -1L)
        if (messageId != -1L) {
            if (isTwoColumnMode) {
                openMessageInDetail(messageId, args)
            } else {
                activity.navigate(navTarget = NavTarget.MESSAGE, args = args)
            }
        }
    }

    fun onMessageStarChanged(messageId: Long, isStarred: Boolean) {
        val detailFrag = childFragmentManager.findFragmentByTag("detail_message") as? MessageFragment
        detailFrag?.onExternalStarChanged(messageId, isStarred)
    }

    private fun updateDetailBackCallback() {
        detailBackCallback.isEnabled = isTwoColumnMode && selectedMessageId != null && !isHidden
    }

    private fun notifyListActiveMessageChanged() {
        val activeId = if (isTwoColumnMode) selectedMessageId else null
        childFragmentManager.fragments.filterIsInstance<MessagesListFragment>().forEach {
            it.setActiveMessageId(activeId)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTwoColumnLayout()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        updateDetailBackCallback()
        if (!hidden && isAdded && view != null) {
            updateTwoColumnLayout()
            consumePendingDetailArgs()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedMessageId?.let { outState.putLong("selectedMessageId", it) }
        selectedMessageArgs?.let { outState.putBundle("selectedMessageArgs", it) }
    }

    override suspend fun onFabClick() {
        activity.navigate(navTarget = NavTarget.MESSAGE_COMPOSE)
    }
}
