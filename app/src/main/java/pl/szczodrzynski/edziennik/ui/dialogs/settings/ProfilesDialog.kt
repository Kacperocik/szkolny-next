package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.card.MaterialCardView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.data.enums.NavTargetLocation
import pl.szczodrzynski.edziennik.databinding.DialogProfilesActionItemBinding
import pl.szczodrzynski.edziennik.databinding.DialogProfilesBinding
import pl.szczodrzynski.edziennik.databinding.DialogProfilesProfileItemBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.onLongClick
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph

class ProfilesDialog(
    activity: MainActivity,
) : BindingDialog<DialogProfilesBinding>(activity) {

    private val mainActivity = activity

    override fun getTitleRes() = R.string.dialog_profiles_title
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogProfilesBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    override fun hasCloseIconWhenSpaceLimited() = true

    override suspend fun onBeforeShow(): Boolean {
        val (profiles, unreadByProfile) = withContext(Dispatchers.IO) {
            val profiles = app.db.profileDao().allNow
            val unreadByProfile = app.db.metadataDao().unreadCountsNow
                .groupBy { it.profileId }
                .mapValues { (_, counters) -> counters.sumOf { it.count } }
            profiles to unreadByProfile
        }
        bindProfiles(profiles, unreadByProfile)
        bindActions()
        return true
    }

    private fun bindProfiles(
        allProfiles: List<Profile>,
        unreadByProfile: Map<Int, Int>,
    ) {
        b.profilesContainer.removeAllViews()
        val allArchived = allProfiles.all { it.archived }
        val profiles = allProfiles.filter {
            it.id >= 0 && (!it.archived || allArchived)
        }.toMutableList()
        if (app.profile.archived && !allArchived) {
            profiles.add(0, app.profile)
        }

        profiles.forEachIndexed { index, profile ->
            val item = DialogProfilesProfileItemBinding.inflate(
                activity.layoutInflater,
                b.profilesContainer,
                false
            )
            profile.applyImageTo(item.image)
            item.name.text = profile.name
            val subname = if (profile.archived && !allArchived)
                "Archiwum - ${profile.subname}"
            else
                profile.subname
            item.subname.text = subname
            item.subname.isVisible = !subname.isNullOrEmpty()

            val unreadCount = unreadByProfile[profile.id] ?: 0
            item.unreadBadge.isVisible = unreadCount > 0
            if (unreadCount > 0) {
                item.unreadBadge.text = if (unreadCount >= 99) "99+" else unreadCount.toString()
                mainActivity.drawer.badgeStyle.style(item.unreadBadge)
                item.unreadBadge.setPadding(3.dp, 1.dp, 3.dp, 1.dp)
                item.unreadBadge.minWidth = 16.dp
                item.unreadBadge.minHeight = 16.dp
                item.unreadBadge.post {
                    val size = maxOf(item.unreadBadge.width, item.unreadBadge.height)
                    if (size > 0) {
                        item.unreadBadge.updateLayoutParams {
                            width = size
                            height = size
                        }
                    }
                }
            }

            val isCurrent = profile.id == App.profileId
            item.currentIcon.isVisible = isCurrent
            if (isCurrent) {
                item.currentIcon.setImageDrawable(
                    CommunityMaterial.Icon.cmd_check.toDrawable(
                        activity,
                        colorAttr = R.attr.colorPrimary
                    )
                )
            }

            item.root.onClick {
                if (!isCurrent) {
                    mainActivity.navigate(profileId = profile.id)
                } else {
                    mainActivity.drawer.close()
                }
                dismiss()
            }
            item.root.onLongClick {
                launch {
                    val appProfile = withContext(Dispatchers.IO) {
                        App.db.profileDao().getByIdNow(profile.id)
                    } ?: return@launch
                    dismiss()
                    mainActivity.drawer.close()
                    ProfileConfigDialog(mainActivity, appProfile).show()
                }
                true
            }

            b.profilesContainer.addView(item.root)
            (item.root as MaterialCardView).applyGroupPosition(index, profiles.size)
            (item.root as MaterialCardView).enableExpressiveCardShapeMorph()
            item.image.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)
        }
        b.profilesContainer.isVisible = profiles.isNotEmpty()
    }

    private fun bindActions() {
        b.actionsContainer.removeAllViews()
        val actions = NavTarget.values().filter {
            it.location == NavTargetLocation.PROFILE_LIST
        }
        val standalone = actions.filter { it == NavTarget.PROFILE_ADD }
        val grouped = actions.filter { it != NavTarget.PROFILE_ADD }
        val iconColor = R.attr.colorOnSurfaceVariant.resolveAttr(activity)

        standalone.forEachIndexed { index, target ->
            addActionItem(target, index, standalone.size, iconColor)
        }
        grouped.forEachIndexed { index, target ->
            addActionItem(
                target,
                index,
                grouped.size,
                iconColor,
                extraTopMargin = if (index == 0 && standalone.isNotEmpty()) 16.dp else 0
            )
        }
        b.actionsContainer.isVisible = actions.isNotEmpty()
        b.actionsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = if (b.profilesContainer.isVisible) 16.dp else 0
        }
    }

    private fun addActionItem(
        target: NavTarget,
        index: Int,
        count: Int,
        iconColor: Int,
        extraTopMargin: Int = 0,
    ) {
        val item = DialogProfilesActionItemBinding.inflate(
            activity.layoutInflater,
            b.actionsContainer,
            false
        )
        item.text.setText(target.nameRes)
        if (target.descriptionRes != null) {
            item.subtext.setText(target.descriptionRes)
            item.subtext.isVisible = true
        } else {
            item.subtext.isVisible = false
        }
        item.icon.setImageDrawable(
            target.icon?.toDrawable(activity, colorInt = iconColor)
        )
        item.root.onClick {
            dismiss()
            mainActivity.onProfileSettingClick(target.id)
        }

        b.actionsContainer.addView(item.root)
        (item.root as MaterialCardView).applyGroupPosition(index, count, extraTopMargin)
        (item.root as MaterialCardView).enableExpressiveCardShapeMorph()
    }

    private fun MaterialCardView.applyGroupPosition(
        index: Int,
        count: Int,
        extraTopMargin: Int = 0,
    ) {
        val density = resources.displayMetrics.density
        val large = 24f * density
        val small = 4f * density
        val isFirst = index == 0
        val isLast = index == count - 1
        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(if (isFirst) large else small)
            .setTopRightCornerSize(if (isFirst) large else small)
            .setBottomLeftCornerSize(if (isLast) large else small)
            .setBottomRightCornerSize(if (isLast) large else small)
            .build()
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = extraTopMargin
            bottomMargin = if (!isLast) 3.dp else 0
        }
    }
}
