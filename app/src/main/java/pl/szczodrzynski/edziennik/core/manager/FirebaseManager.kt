/*
 * Copyright (c) Kuba Szczodrzyński 2024-7-1.
 */

package pl.szczodrzynski.edziennik.core.manager

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import pl.szczodrzynski.edziennik.App
import timber.log.Timber

class FirebaseManager(val app: App) {
    data class Instance(
        val name: String,
        val projectId: String,
        val projectNumber: String,
        val apiKey: String,
        val applicationId: String,
        val onToken: (token: String) -> Unit,
    )

    private val instances = listOf(
        Instance(
            name = "Mobidziennik",
            projectId = "mobidziennik",
            projectNumber = "747285019373",
            apiKey = "AIzaSyCi5LmsZ5BBCQnGtrdvWnp1bWLCNP8OWQE",
            applicationId = "f6341bf7b158621d",
            onToken = {
                app.config.sync.tokenMobidziennik = it
                app.config.sync.tokenMobidziennikList = listOf()
            },
        ),
        Instance(
            name = "Librus",
            projectId = "synergiadru",
            projectNumber = "513056078587",
            apiKey = "AIzaSyDfTuEoYPKdv4aceEws1CO3n0-HvTndz-o",
            applicationId = "1e29083b760af544",
            onToken = {
                app.config.sync.tokenLibrus = it
                app.config.sync.tokenLibrusList = listOf()
            },
        ),
        Instance(
            name = "Vulcan",
            projectId = "dzienniczekplus",
            projectNumber = "987828170337",
            apiKey = "AIzaSyDW8MUtanHy64_I0oCpY6cOxB3jrvJd_iA",
            applicationId = "ac97431a0a4578c3",
            onToken = {
                app.config.sync.tokenVulcan = it
                app.config.sync.tokenVulcanList = listOf()
            },
        ),
        Instance(
            name = "VulcanHebe",
            projectId = "dzienniczekplus",
            projectNumber = "987828170337",
            apiKey = "AIzaSyDW8MUtanHy64_I0oCpY6cOxB3jrvJd_iA",
            applicationId = "7e16404b9e5deaaa",
            onToken = {
                app.config.sync.tokenVulcanHebe = it
                app.config.sync.tokenVulcanHebeList = listOf()
            },
        ),
    )

    fun initializeApps() {
        try {
            FirebaseApp.getInstance()
            FirebaseInstanceId.getInstance().instanceId
                .addOnSuccessListener { result ->
                    app.config.sync.tokenApp = result.token
                }
                .addOnFailureListener { Timber.w(it, "Failed to get App push token") }
            FirebaseMessaging.getInstance().subscribeToTopic(app.packageName)
                .addOnFailureListener { Timber.w(it, "Failed to subscribe to App push topic") }
        } catch (e: Exception) {
            Timber.w(e, "Failed to initialize App push notifications")
        }

        instances.forEach { config ->
            try {
                val instance = FirebaseApp.getApps(app).firstOrNull { it.name == config.name }
                    ?: FirebaseApp.initializeApp(app, FirebaseOptions.Builder()
                        .setProjectId(config.projectId)
                        .setStorageBucket("${config.projectId}.appspot.com")
                        .setDatabaseUrl("https://${config.projectId}.firebaseio.com")
                        .setGcmSenderId(config.projectNumber)
                        .setApiKey(config.apiKey)
                        .setApplicationId("1:${config.projectNumber}:android:${config.applicationId}")
                        .build(), config.name)

                FirebaseInstanceId.getInstance(instance).instanceId
                    .addOnSuccessListener { result ->
                        val token = result.token
                        if (token.isNotBlank() && token != app.config["token${config.name}"])
                            config.onToken(token)
                    }
                    .addOnFailureListener { Timber.w(it, "Failed to get %s push token", config.name) }
            } catch (e: Exception) {
                Timber.w(e, "Failed to initialize %s push notifications", config.name)
            }
        }
    }
}
