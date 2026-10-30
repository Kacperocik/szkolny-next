/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-23.
 */

package pl.szczodrzynski.edziennik.ui.home

import android.content.res.Configuration
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon
import eu.szkolny.font.SzkolnyFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.databinding.FragmentHomeBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.hasUIFeature
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.spinGear360
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.dialogs.settings.HomeConfigDialog
import pl.szczodrzynski.edziennik.ui.dialogs.settings.StudentNumberDialog
import pl.szczodrzynski.edziennik.ui.home.cards.HomeAnnouncementsCard
import pl.szczodrzynski.edziennik.ui.home.cards.HomeArchiveCard
import pl.szczodrzynski.edziennik.ui.home.cards.HomeAvailabilityCard
import pl.szczodrzynski.edziennik.ui.home.cards.HomeEventsCard
import pl.szczodrzynski.edziennik.ui.home.cards.HomeGradesCard
import pl.szczodrzynski.edziennik.ui.home.cards.HomeLuckyNumberCard
import pl.szczodrzynski.edziennik.ui.home.cards.HomeNotesCard
import pl.szczodrzynski.edziennik.ui.home.cards.HomeSurveysCard
import pl.szczodrzynski.edziennik.ui.home.cards.HomeTimetableCard
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetGroup
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem

class HomeFragment : BaseFragment<FragmentHomeBinding, MainActivity>(
    inflater = FragmentHomeBinding::inflate,
) {
    companion object {
        private const val TWO_COLUMNS_MIN_WIDTH_DP = 720

        fun swapCards(fromPosition: Int, toPosition: Int, cardAdapter: HomeCardAdapter): Boolean {
            val fromCard = cardAdapter.items[fromPosition]
            val toCard = cardAdapter.items[toPosition]
            if (fromCard.id >= 100 || toCard.id >= 100) {
                // debug & archive cards are not swappable
                return false
            }
            cardAdapter.items[fromPosition] = cardAdapter.items[toPosition]
            cardAdapter.items[toPosition] = fromCard
            cardAdapter.notifyItemMoved(fromPosition, toPosition)

            val homeCards = App.profile.config.ui.homeCards.toMutableList()
            val fromIndex = homeCards.indexOfFirst { it.cardId == fromCard.id }
            val toIndex = homeCards.indexOfFirst { it.cardId == toCard.id }
            val fromPair = homeCards[fromIndex]
            homeCards[fromIndex] = homeCards[toIndex]
            homeCards[toIndex] = fromPair
            App.profile.config.ui.homeCards = homeCards
            return true
        }

        fun removeCard(position: Int, cardAdapter: HomeCardAdapter) {
            val homeCards = App.profile.config.ui.homeCards.toMutableList()
            val card = cardAdapter.items.getOrNull(position) ?: return
            if (card.id >= 100) {
                // debug & archive cards are not removable
                return
            }
            homeCards.removeAll { it.cardId == card.id }
            App.profile.config.ui.homeCards = homeCards
        }
    }

    override fun getScrollingView() = b.scrollView
    override fun getSyncParams() = null to null
    override fun getBottomSheetItems() = listOf(
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_add_remove_cards)
            .withIcon(Icon.cmd_card_bulleted_settings_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                HomeCardsDialog(activity, reloadOnDismiss = true).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_home_config)
            .withIcon(Icon.cmd_cog_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                HomeConfigDialog(activity, reloadOnDismiss = true).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_set_student_number)
            .withIcon(SzkolnyFont.Icon.szf_clipboard_list_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                StudentNumberDialog(activity, app.profile).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_mark_everything_as_read)
            .withIcon(Icon.cmd_eye_check_outline)
            .withGroup(BottomSheetGroup.ACTIONS)
            .withOnClickListener {
                activity.bottomSheet.close()
                activity.markAllAsRead(App.profileId)
            }
    )

    private val manager
        get() = app.permissionManager

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        if (!manager.isNotificationPermissionGranted) {
            manager.requestNotificationsPermission(activity, 0, false) {}
        }

        b.configureCards.icon = Icon.cmd_cog_outline.toDrawable(activity)
        val homeCardsLocked = app.profile.config.ui.homeCardsLocked
        if (homeCardsLocked) {
            b.configHintText.setText(R.string.home_configure_locked)
        } else {
            b.configHintText.setText(R.string.home_configure_notice)
        }
        b.configureCards.onClick {
            HomeConfigDialog(activity, reloadOnDismiss = true).show()
        }
        b.configureCards.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            b.configureCards.spinGear360()
            true
        }

        val cards = app.profile.config.ui.homeCards.filter { it.profileId == app.profile.id }.toMutableList()
        if (cards.isEmpty()) {
            cards += listOfNotNull(
                HomeCardModel(app.profile.id, HomeCard.CARD_LUCKY_NUMBER).takeIf { app.profile.hasUIFeature(
                    FeatureType.LUCKY_NUMBER) },
                HomeCardModel(app.profile.id, HomeCard.CARD_TIMETABLE).takeIf { app.profile.hasUIFeature(
                    FeatureType.TIMETABLE) },
                HomeCardModel(app.profile.id, HomeCard.CARD_EVENTS).takeIf { app.profile.hasUIFeature(
                    FeatureType.AGENDA) },
                HomeCardModel(app.profile.id, HomeCard.CARD_GRADES).takeIf { app.profile.hasUIFeature(
                    FeatureType.GRADES) },
                HomeCardModel(app.profile.id, HomeCard.CARD_NOTES),
            )
            app.profile.config.ui.homeCards = app.profile.config.ui.homeCards.toMutableList().also { it.addAll(cards) }
        }

        val items = mutableListOf<HomeCard>()
        cards.mapNotNullTo(items) {
            @Suppress("USELESS_CAST")
            when (it.cardId) {
                HomeCard.CARD_LUCKY_NUMBER -> HomeLuckyNumberCard(it.cardId, app, activity, this, app.profile)
                HomeCard.CARD_TIMETABLE -> HomeTimetableCard(it.cardId, app, activity, this, app.profile)
                HomeCard.CARD_GRADES -> HomeGradesCard(it.cardId, app, activity, this, app.profile)
                HomeCard.CARD_EVENTS -> HomeEventsCard(it.cardId, app, activity, this, app.profile)
                HomeCard.CARD_ANNOUNCEMENTS -> HomeAnnouncementsCard(it.cardId, app, activity, this, app.profile)
                HomeCard.CARD_NOTES -> HomeNotesCard(it.cardId, app, activity, this, app.profile)
                else -> null
            } as HomeCard?
        }
        if (app.profile.archived)
            items.add(0, HomeArchiveCard(HomeCard.CARD_ARCHIVE, app, activity, this, app.profile))

        val status = app.availabilityManager.check(app.profile, cacheOnly = true)?.status
        val update = app.config.update
        if (update != null && app.updateManager.isApplicable(update) || status?.userMessage != null) {
            items.add(0, HomeAvailabilityCard(HomeCard.CARD_AVAILABILITY, app, activity, this, app.profile))
        }

        val list = b.list
        val layoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        var isMovingCard = false
        val rebalanceCards = Runnable {
            if (layoutManager.spanCount > 1 && !isMovingCard)
                layoutManager.invalidateSpanAssignments()
        }
        val scheduleRebalance = {
            // Cards load their content asynchronously. Forget the columns chosen for
            // their initially empty views and place them using their actual heights.
            // Coalesce size changes and wait until the current layout has finished.
            if (layoutManager.spanCount > 1) {
                list.removeCallbacks(rebalanceCards)
                list.postOnAnimation(rebalanceCards)
            }
        }
        val adapter = HomeCardAdapter(
            items,
            isCardsLocked = { app.profile.config.ui.homeCardsLocked },
            onCardSizeChanged = scheduleRebalance,
        )
        b.list.layoutManager = layoutManager
        b.list.adapter = adapter
        b.list.isNestedScrollingEnabled = false
        if (app.profile.loginStoreType == LoginType.LIBRUS && app.profile.config.ui.homeSurveysEnabled) {
            app.db.librusSurveyDao().getAll(app.profile.id).observe(viewLifecycleOwner) { surveys ->
                val index = adapter.items.indexOfFirst { it.id == HomeCard.CARD_SURVEYS }
                if (surveys.isEmpty()) {
                    if (index >= 0) {
                        adapter.items.removeAt(index)
                        adapter.notifyItemRemoved(index)
                    }
                } else if (index >= 0) {
                    (adapter.items[index] as HomeSurveysCard).surveys = surveys
                    adapter.notifyItemChanged(index)
                } else {
                    adapter.items.add(0, HomeSurveysCard(activity, surveys))
                    adapter.notifyItemInserted(0)
                }
                scheduleRebalance()
            }
        }
        updateColumns()
        b.scrollView.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft)
                b.list.post { updateColumns() }
        }

        if (!homeCardsLocked) {
            val autoScroller = HomeCardDragAutoScroller(b.list, b.scrollView)
            val itemTouchHelper = ItemTouchHelper(CardItemTouchHelperCallback(
                adapter,
                onDragStateChanged = autoScroller::setDragging,
            ) {
                canRefreshDisabled = !it
                isMovingCard = !it
                if (it) scheduleRebalance()
            })
            adapter.itemTouchHelper = itemTouchHelper
            b.list.setAccessibilityDelegateCompat(object : RecyclerViewAccessibilityDelegate(b.list) {
                override fun getItemDelegate(): AccessibilityDelegateCompat {
                    return object : ItemDelegate(this) {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            val position: Int = b.list.getChildLayoutPosition(host)
                            if (position != 0) {
                                info.addAction(AccessibilityActionCompat(
                                    R.id.move_card_up_action,
                                    host.resources.getString(R.string.card_action_move_up)
                                ))
                            }
                            if (position != adapter.itemCount - 1) {
                                info.addAction(AccessibilityActionCompat(
                                    R.id.move_card_down_action,
                                    host.resources.getString(R.string.card_action_move_down)
                                ))
                            }
                        }

                        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                            val fromPosition: Int = b.list.getChildLayoutPosition(host)
                            if (action == R.id.move_card_down_action) {
                                swapCards(fromPosition, fromPosition + 1, adapter)
                                return true
                            } else if (action == R.id.move_card_up_action) {
                                swapCards(fromPosition, fromPosition - 1, adapter)
                                return true
                            }
                            return super.performAccessibilityAction(host, action, args)
                        }
                    }
                }
            })
            itemTouchHelper.attachToRecyclerView(b.list)
        }
    }

    /**
     * Shows the cards in two columns in landscape, but only if each
     * column is at least as wide as a typical phone screen.
     */
    private fun updateColumns() {
        if (!isAdded) return
        val layoutManager = b.list.layoutManager as? StaggeredGridLayoutManager ?: return
        val adapter = b.list.adapter as? HomeCardAdapter ?: return
        val widthDp = b.scrollView.width / resources.displayMetrics.density
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape && widthDp >= TWO_COLUMNS_MIN_WIDTH_DP) 2 else 1
        if (layoutManager.spanCount == spanCount) return

        // keep the outer margins at 16dp, with a 16dp gap between the columns
        val cardMargin = if (spanCount > 1) 8.dp else 16.dp
        b.list.updatePadding(left = 16.dp - cardMargin, right = 16.dp - cardMargin)
        adapter.cardHorizontalMargin = cardMargin
        b.list.children.forEach {
            it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = cardMargin
                rightMargin = cardMargin
            }
        }
        layoutManager.spanCount = spanCount
    }
}
