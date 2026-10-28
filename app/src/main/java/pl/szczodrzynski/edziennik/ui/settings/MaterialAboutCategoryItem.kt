package pl.szczodrzynski.edziennik.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.danielstone.materialaboutlibrary.holders.MaterialAboutItemViewHolder
import com.danielstone.materialaboutlibrary.items.MaterialAboutItem
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.google.android.material.card.MaterialCardView
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.resolveString
import pl.szczodrzynski.edziennik.ext.toDrawable

class MaterialAboutCategoryItem(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: IIcon,
    val onClick: () -> Unit,
) : MaterialAboutItem() {

    override fun getType() = SettingsViewTypeManager.ItemType.CATEGORY_ITEM

    override fun getDetailString(): String = titleRes.toString()

    override fun clone(): MaterialAboutItem = MaterialAboutCategoryItem(
        titleRes = titleRes,
        subtitleRes = subtitleRes,
        icon = icon,
        onClick = onClick,
    )

    class ViewHolder(view: View) : MaterialAboutItemViewHolder(view) {
        val card: View = view.findViewById(R.id.mal_category_card)
        val iconContainer: FrameLayout = view.findViewById(R.id.mal_category_icon_container)
        val iconView: ImageView = view.findViewById(R.id.mal_category_icon)
        val titleView: TextView = view.findViewById(R.id.mal_category_title)
        val subtitleView: TextView = view.findViewById(R.id.mal_category_subtitle)
        val chevronView: ImageView = view.findViewById(R.id.mal_category_chevron)
    }

    companion object {
        fun getViewHolder(view: View) = ViewHolder(view)

        fun setupItem(
            holder: ViewHolder,
            item: MaterialAboutCategoryItem,
            context: Context
        ) {
            holder.titleView.text = item.titleRes.resolveString(context)
            holder.subtitleView.text = item.subtitleRes.resolveString(context)

            val primaryContainer = R.attr.colorPrimaryContainer.resolveAttr(context)
            val onPrimaryContainer = R.attr.colorOnPrimaryContainer.resolveAttr(context)

            ViewCompat.setBackgroundTintList(holder.iconContainer, ColorStateList.valueOf(primaryContainer))
            holder.iconView.setImageDrawable(item.icon.toDrawable(context, onPrimaryContainer))
            holder.chevronView.setImageDrawable(
                CommunityMaterial.Icon.cmd_chevron_right.toDrawable(
                    context,
                    R.attr.colorOnSurfaceVariant.resolveAttr(context)
                )
            )

            val cardView = holder.card as? MaterialCardView
            cardView?.isClickable = true
            cardView?.isFocusable = true
            cardView?.setOnClickListener {
                item.onClick()
            }
            cardView?.enableExpressiveCardShapeMorph()
        }
    }
}
