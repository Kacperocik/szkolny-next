package pl.szczodrzynski.edziennik.ui.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import pl.szczodrzynski.edziennik.data.enums.NavTarget

internal fun FragmentManager.navigateSettingsHierarchy(
    transaction: FragmentTransaction,
    containerId: Int,
    previousTarget: NavTarget,
    target: NavTarget,
    fragment: Fragment,
    profileChanged: Boolean = false,
): Boolean {
    val fragments = fragments.filter { it.id == containerId }
    if (target.popTo == NavTarget.SETTINGS) {
        val parent = fragments.lastOrNull { it is SettingsFragment }?.takeUnless { profileChanged }
        if (parent == null) {
            fragments.forEach(transaction::remove)
            val settings = SettingsFragment()
            transaction.add(containerId, settings)
            transaction.hide(settings)
        } else {
            fragments.drop(fragments.indexOf(parent) + 1).forEach(transaction::remove)
            transaction.hide(parent)
        }
        transaction.add(containerId, fragment)
        return true
    }
    if (!profileChanged && previousTarget.popTo == NavTarget.SETTINGS && target in setOf(NavTarget.WEB_PUSH, NavTarget.CONTRIBUTORS)) {
        // Keep both settings levels underneath the additional page for back navigation.
        fragments.lastOrNull()?.let(transaction::hide)
        transaction.add(containerId, fragment)
        return true
    }
    return false
}
