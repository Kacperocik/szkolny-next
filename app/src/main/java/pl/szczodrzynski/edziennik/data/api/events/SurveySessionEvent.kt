package pl.szczodrzynski.edziennik.data.api.events

// Delivered only to the requesting view; never retained as a sticky event.
class SurveySessionEvent(val profileId: Int, val requestId: String, val sessionId: String)
