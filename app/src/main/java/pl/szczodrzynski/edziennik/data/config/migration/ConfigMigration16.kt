package pl.szczodrzynski.edziennik.data.config.migration

import pl.szczodrzynski.edziennik.data.config.BaseMigration
import pl.szczodrzynski.edziennik.data.config.Config
import pl.szczodrzynski.edziennik.data.enums.NavTarget

class ConfigMigration16 : BaseMigration<Config>() {
    override fun migrate(config: Config) = config.apply {
        ui.drawerMoreButtons = ui.drawerMoreButtons + NavTarget.LESSON_TOPICS
    }
}
