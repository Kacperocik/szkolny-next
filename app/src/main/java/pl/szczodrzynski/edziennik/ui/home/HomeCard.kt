/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-23.
 */

package pl.szczodrzynski.edziennik.ui.home

interface HomeCard {
    companion object {
        /**
         * A card is visible on every profile.
         * On a unified home fragment, shows
         * summary data from every profile.
         * Not every card may work like this.
         */
        const val PROFILE_UNIFIED = -1
        /**
         * A card is visible on every profile, but does
         * not show every profile, just the currently
         * loaded one.
         */
        const val PROFILE_ALL = 0

        const val CARD_LUCKY_NUMBER = 1
        const val CARD_TIMETABLE = 2
        const val CARD_GRADES = 3
        const val CARD_EVENTS = 4
        const val CARD_NOTES = 5
        const val CARD_ANNOUNCEMENTS = 6

        // IDs >= 100 identify cards with a fixed position.
        const val CARD_ARCHIVE = 101
        const val CARD_AVAILABILITY = 102
        const val CARD_SURVEYS = 103
    }

    val id: Int

    fun bind(position: Int, holder: HomeCardAdapter.ViewHolder)
    fun unbind(position: Int, holder: HomeCardAdapter.ViewHolder)
}
