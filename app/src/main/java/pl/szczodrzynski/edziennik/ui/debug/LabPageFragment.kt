/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-12.
 */

@file:Suppress("DEPRECATION_ERROR")

package pl.szczodrzynski.edziennik.ui.debug

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.sqlite.db.SimpleSQLiteQuery
import com.chuckerteam.chucker.api.Chucker
import com.chuckerteam.chucker.api.Chucker.SCREEN_HTTP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.szkolny.interceptor.SignatureInterceptor
import pl.szczodrzynski.edziennik.data.config.Config
import pl.szczodrzynski.edziennik.data.db.ProfileBackupManager
import pl.szczodrzynski.edziennik.data.db.entity.EventType.Companion.SOURCE_DEFAULT
import pl.szczodrzynski.edziennik.databinding.LabFragmentBinding
import pl.szczodrzynski.edziennik.ext.asBoldSpannable
import pl.szczodrzynski.edziennik.ext.asColoredSpannable
import pl.szczodrzynski.edziennik.ext.asItalicSpannable
import pl.szczodrzynski.edziennik.ext.asUnderlineSpannable
import pl.szczodrzynski.edziennik.ext.concat
import pl.szczodrzynski.edziennik.ext.onChange
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.startCoroutineTimer
import pl.szczodrzynski.edziennik.ext.takeValue
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.dialogs.ProfileRemoveDialog
import pl.szczodrzynski.edziennik.ui.dialogs.RestartDialog
import pl.szczodrzynski.edziennik.utils.TextInputDropDown
import pl.szczodrzynski.fslogin.decode

class LabPageFragment : BaseFragment<LabFragmentBinding, AppCompatActivity>(
    inflater = LabFragmentBinding::inflate,
) {

    private val profileBackupManager by lazy {
        ProfileBackupManager(app.db)
    }

    private val exportProfileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@registerForActivityResult
        launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val output = requireNotNull(activity.contentResolver.openOutputStream(uri)) {
                        "Unable to open the selected file"
                    }
                    output.bufferedWriter().use { writer ->
                        profileBackupManager.export(app.profile.id, writer)
                    }
                }
            }
            showFileResult(result, "Profile exported")
        }
    }

    private val importProfileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        confirmProfileImport(uri)
    }

    override fun getScrollingView() = b.scrollView

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        b.app = app

        if (app.profile.id == 0) {
            b.last10unseen.isVisible = false
            b.fullSync.isVisible = false
            b.clearProfile.isVisible = false
            b.clearEndpointTimers.isVisible = false
            b.rodo.isVisible = false
            b.removeHomework.isVisible = false
            b.resetEventTypes.isVisible = false
            b.unarchive.isVisible = false
            b.profile.isVisible = false
            b.clearConfigProfile.isVisible = false
            b.exportProfile.isVisible = false
            b.importProfile.isVisible = false
        }

        b.last10unseen.onClick {
            launch(Dispatchers.Default) {
                val events = app.db.eventDao().getAllNow(App.profileId)
                val ids = events.sortedBy { it.date }.filter { it.isHomework }.takeLast(10)
                ids.forEach {
                    app.db.metadataDao().setSeen(App.profileId, it, false)
                }
            }
        }

        b.rodo.onClick {
            app.db.teacherDao().query(SimpleSQLiteQuery("UPDATE teachers SET teacherSurname = \"\" WHERE profileId = ${App.profileId}"))
        }

        b.fullSync.onClick {
            app.profile.empty = true
            app.profileSave()
        }

        b.clearProfile.onClick {
            ProfileRemoveDialog(activity, App.profileId, "FAKE", noProfileRemoval = true).show()
        }

        b.clearEndpointTimers.onClick {
            app.db.endpointTimerDao().clear(app.profileId)
        }

        b.removeHomework.onClick {
            app.db.eventDao().getRawNow("UPDATE events SET homeworkBody = NULL WHERE profileId = ${App.profileId}")
        }

        b.resetEventTypes.onClick {
            app.db.eventTypeDao().clearBySource(App.profileId, SOURCE_DEFAULT)
            app.db.eventTypeDao().getAllWithDefaults(App.profile)
        }

        b.chucker.isChecked = App.enableChucker
        b.chucker.setOnCheckedChangeListener { _, isChecked ->
            app.config.enableChucker = isChecked
            App.enableChucker = isChecked
            RestartDialog(activity).show()
        }

        b.labOldAgenda.isChecked = app.config.labOldAgenda
        b.labOldAgenda.setOnCheckedChangeListener { _, isChecked ->
            app.config.labOldAgenda = isChecked
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.setDrawerItems()
                mainActivity.reloadTarget()
            }
        }

        if (App.enableChucker) {
            b.openChucker.isVisible = true
            b.openChucker.onClick {
                startActivity(Chucker.getLaunchIntent(activity, SCREEN_HTTP))
            }
        }

        b.disableDebug.onClick {
            app.config.devMode = false
            App.devMode = false
            RestartDialog(activity).show()
        }

        b.unarchive.onClick {
            app.profile.archived = false
            app.profile.archiveId = null
            app.profileSave()
        }

        b.exportProfile.onClick {
            exportProfileLauncher.launch("szkolny-profile-${app.profile.id}.json")
        }

        b.importProfile.onClick {
            importProfileLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
        }

        b.resetCert.onClick {
            app.config.apiInvalidCert = null
        }

        b.apiKey.setText(app.config.apiKeyCustom ?: SignatureInterceptor.API_KEY)
        b.apiKey.doAfterTextChanged {
            it?.toString()?.let { key ->
                if (key == SignatureInterceptor.API_KEY)
                    app.config.apiKeyCustom = null
                else
                    app.config.apiKeyCustom = key.takeValue()?.trim()
            }
        }

        b.clearConfigProfile.onClick {
            app.db.configDao().clear(app.profileId)
        }
        b.clearConfigGlobal.onClick {
            app.db.configDao().clear(-1)
        }
        b.rebuildConfig.onClick {
            App.config = Config(app)
            App.config.migrate()
        }

        val profiles = app.db.profileDao().allNow
        b.profile.clear()
        b.profile += profiles.map { TextInputDropDown.Item(it.id.toLong(), "${it.id} ${it.name} archived ${it.archived}", tag = it) }
        b.profile.select(app.profileId.toLong())
        b.profile.setOnChangeListener {
            if (activity is MainActivity)
                (activity as MainActivity).navigate(profileId = it.id.toInt())
            return@setOnChangeListener true
        }

        b.clearCookies.onClick {
            app.cookieJar.clearAllDomains()
        }

        val colorSecondary = android.R.attr.textColorSecondary.resolveAttr(activity)
        startCoroutineTimer(500L, 300L) {
            val text = app.cookieJar.getAllDomains()
                .sortedBy { it.domain() }
                .groupBy { it.domain() }
                .map { pair ->
                    listOf(
                        pair.key.asBoldSpannable(),
                        ":\n",
                        pair.value
                            .sortedBy { it.name() }
                            .map { cookie ->
                                listOf(
                                    "    ",
                                    if (cookie.persistent())
                                        cookie.name()
                                            .asUnderlineSpannable()
                                    else
                                        cookie.name(),
                                    "=",
                                    cookie.value()
                                        .decode()
                                        .take(40)
                                        .asItalicSpannable()
                                        .asColoredSpannable(colorSecondary),
                                ).concat("")
                            }.concat("\n")
                    ).concat("")
                }.concat("\n\n")
            b.cookies.text = text
        }
    }

    private fun showFileResult(result: Result<Unit>, successMessage: String) {
        val message = result.fold(
            onSuccess = { successMessage },
            onFailure = { "Profile file error: ${it.message ?: it.javaClass.simpleName}" },
        )
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun confirmProfileImport(uri: Uri) {
        AlertDialog.Builder(activity)
            .setTitle("Import full profile backup")
            .setMessage(
                "All data of the current profile will be replaced, including grades, " +
                    "messages, events, attendance, timetable, notes and settings."
            )
            .setPositiveButton("Import") { _, _ ->
                importProfile(uri)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importProfile(uri: Uri) {
        launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val input = requireNotNull(activity.contentResolver.openInputStream(uri)) {
                        "Unable to open the selected file"
                    }
                    input.bufferedReader().use { reader ->
                        profileBackupManager.import(app.profile.id, reader)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    Toast.makeText(activity, "Full profile backup imported", Toast.LENGTH_LONG)
                        .show()
                    RestartDialog(activity).show()
                },
                onFailure = {
                    Toast.makeText(
                        activity,
                        "Profile file error: ${it.message ?: it.javaClass.simpleName}",
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }
}
