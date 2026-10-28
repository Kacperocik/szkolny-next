/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-10.
 */

package pl.szczodrzynski.edziennik.ui.login.viewholder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.LoginChooserItemBinding
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.grades.viewholder.BindableViewHolder
import pl.szczodrzynski.edziennik.ui.login.LoginChooserAdapter
import pl.szczodrzynski.edziennik.ui.login.LoginInfo

class RegisterViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: LoginChooserItemBinding = LoginChooserItemBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<LoginInfo.Register, LoginChooserAdapter> {

    override fun onBind(activity: AppCompatActivity, app: App, item: LoginInfo.Register, position: Int, adapter: LoginChooserAdapter) {
        b.logo.setImageResource(item.registerLogo)
        b.name.setText(item.registerName)
        b.description.isVisible = false

        if (item.loginModes.size > 1) {
            b.chevron.isVisible = true
            b.chevron.setImageDrawable(
                CommunityMaterial.Icon.cmd_chevron_down.toDrawable(activity, colorAttr = android.R.attr.textColorSecondary)
            )
            val isOpened = item.state == LoginChooserAdapter.STATE_OPENED
            val targetRotation = if (isOpened) 180f else 0f
            if (b.chevron.rotation != targetRotation) {
                b.chevron.animate()
                    .rotation(targetRotation)
                    .setDuration(200)
                    .start()
            }
        } else {
            b.chevron.isVisible = false
        }

        b.card.enableExpressiveCardShapeMorph()
    }
}
