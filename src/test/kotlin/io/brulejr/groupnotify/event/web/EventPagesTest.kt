/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.web

import io.brulejr.groupnotify.event.EventDraft
import io.brulejr.groupnotify.event.EventProperty
import io.brulejr.groupnotify.event.EventResult
import io.brulejr.groupnotify.event.EventService
import io.brulejr.groupnotify.event.ReminderTiming
import io.brulejr.groupnotify.event.ReminderUnit
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.ScheduleInput
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.security.NotifyUser
import io.brulejr.groupnotify.support.WebTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Every event page renders, and the editor, notes, actions and REST API work, through the security filters. */
class EventPagesTest : WebTest() {

    @Autowired
    lateinit var events: EventService

    private val alice = NotifyUser("alice", "Alice", null, setOf("notify_club"), admin = false)

    /** A club meeting every Tuesday at 7 PM, reminded a day and an hour before, through the log plugin. */
    private fun seed(): UUID {
        val log = provision()
        val draft = EventDraft(
            group = "notify_club", title = "Club meeting", subject = "Meeting {{event.when}} at {{event.location}}",
            body = "See you at **{{event.location}}**.", format = BodyFormat.MARKDOWN,
            schedule = ScheduleInput(ScheduleKind.PATTERN, "America/New_York", pattern = "every tuesday at 7pm"),
            properties = listOf(EventProperty("location", "Town hall")),
            reminders = listOf(
                EventDraft.Reminder(amount = "1", unit = ReminderUnit.DAYS, timing = ReminderTiming.BEFORE, deliveries = listOf(NotificationDraft.Delivery(instance = log.id.toString()))),
                EventDraft.Reminder(
                    amount = "1", unit = ReminderUnit.HOURS, timing = ReminderTiming.BEFORE, subject = "Starting {{event.when}}",
                    deliveries = listOf(NotificationDraft.Delivery(instance = log.id.toString(), overrides = mapOf("body" to "text"))),
                ),
            ),
        )
        return (events.create(draft, alice) as EventResult.Saved).event.id
    }

    @Test
    fun `every event page renders, and another group's is not found`() {
        val id = seed()
        val pages = mapOf(
            "/ui/events" to "Club meeting",
            "/ui/events/new" to "New event",
            "/ui/events/$id" to "Coming occurrences",
            "/ui/events/$id/body" to "Add note",
            "/ui/events/$id/edit" to "properties[0].name",
            "/ui/events/reminder?index=4&group=notify_club" to "notifyNextIn(&quot;r4-deliveries&quot;)",
            "/ui/messages/delivery?instance=${eventStore.find(id)!!.reminders[0].deliveries[0].instance}&index=2&prefix=reminders[4].deliveries" to "reminders[4].deliveries[2].overrides.body",
            "/ui/messages/instances?group=notify_club&list=r4-deliveries&prefix=reminders[4].deliveries" to "#r4-deliveries",
            "/ui/events/property?index=2" to "properties[2].value",
            "/ui/history" to "Club meeting",
            "/ui" to "href=\"/ui/events\"",
        )
        pages.forEach { (path, text) ->
            mvc.perform(get(path).alice()).andExpect(status().isOk).andExpect(content().string(containsString(text)))
        }
        mvc.perform(get("/ui/events/$id").alice()).andExpect(content().string(containsString("See you at <strong>Town hall</strong>.")))
        mvc.perform(get("/ui/events/$id").bob()).andExpect(status().isNotFound)
    }

    @Test
    fun `the dashboard shows the next events and their reminders among what is coming up`() {
        val id = seed()

        mvc.perform(get("/ui/overview").alice()).andExpect(status().isOk)
            .andExpect(content().string(containsString("Next events")))
            .andExpect(content().string(containsString("href=\"/ui/events/$id\"")))
            .andExpect(content().string(containsString("Club meeting")))
            // Coming up, beside the notifications: the event's next reminder, by its offset.
            .andExpect(content().string(org.hamcrest.Matchers.anyOf(containsString(">1 hour before<"), containsString(">1 day before<"))))
            // Its subject as that reminder sends it, filled.
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("{{event.when}}"))))
        mvc.perform(get("/ui/overview").bob()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Club meeting"))))
    }

    @Test
    fun `saving the form creates the event, and a refused one is redrawn against its inputs`() {
        val log = provision()
        fun form() = post("/ui/events").alice().with(csrf())
            .param("group", "notify_club").param("title", "Field day").param("subject", "Field day {{event.when}}")
            .param("format", "MARKDOWN").param("body", "At {{event.site}}")
            .param("kind", "PATTERN").param("pattern", "every month on the 4th saturday at 9am").param("zone", "America/New_York")
            .param("properties[0].name", "site").param("properties[0].value", "The park")
            .param("reminders[3].amount", "2").param("reminders[3].unit", "DAYS").param("reminders[3].timing", "BEFORE")
            .param("reminders[3].deliveries[7].instance", log.id.toString()).param("reminders[3].deliveries[7].overrides.body", "text")

        mvc.perform(form()).andExpect(header().string("HX-Redirect", startsWith("/ui/events/")))
        val saved = eventStore.list(null).single()
        assertEquals(listOf(EventProperty("site", "The park")), saved.properties)
        assertEquals("2 days before", saved.reminders.single().describe())
        assertEquals(listOf(mapOf("body" to "text")), saved.reminders.single().deliveries.map { it.overrides })
        mvc.perform(get("/ui/events/${saved.id}/edit").alice()).andExpect(content().string(containsString("reminders[0].deliveries[0].instance")))

        mvc.perform(form().param("properties[1].name", "2nd").param("reminders[5].amount", "nope"))
            .andExpect(header().doesNotExist("HX-Redirect"))
            .andExpect(content().string(containsString("must be letters, digits and _")))
            .andExpect(content().string(containsString("must be a whole number")))
        // A reminder with nowhere to go.
        mvc.perform(post("/ui/events").alice().with(csrf()).param("group", "notify_club").param("title", "T").param("subject", "S")
            .param("body", "B").param("kind", "PATTERN").param("pattern", "every day at 9am").param("reminders[0].amount", "1").param("reminders[0].unit", "HOURS"))
            .andExpect(content().string(containsString("Add at least one delivery")))
    }

    @Test
    fun `a member writes an occurrence's note on its own page, sends now, and deletes the event`() {
        val id = seed()
        val occurrence = events.occurrences(eventStore.find(id)!!, 1).single()
        val page = "/ui/events/$id/notes/${occurrence.epochSecond}"

        // The note's page has the body's editor; the event's page only links to it.
        mvc.perform(get(page).alice()).andExpect(status().isOk)
            .andExpect(content().string(containsString("data-editor")))
            .andExpect(content().string(containsString("data-format-switch")))
        mvc.perform(get("/ui/events/$id").alice()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-editor"))))
            .andExpect(content().string(containsString(page)))
        mvc.perform(get(page).bob()).andExpect(status().isNotFound)
        mvc.perform(get("/ui/events/$id/notes/${occurrence.epochSecond + 60}").alice()).andExpect(status().isNotFound)

        mvc.perform(post(page).alice().with(csrf()).param("body", "<p>Guest <em>speaker</em></p>").param("format", "HTML"))
            .andExpect(header().string("HX-Redirect", "/ui/events/$id?saved=note"))
        assertEquals("<p>Guest <em>speaker</em></p>", eventStore.find(id)!!.notes[occurrence]?.text)
        mvc.perform(get("/ui/events/$id?saved=note").alice())
            .andExpect(content().string(containsString("Guest <em>speaker</em>")))
            .andExpect(content().string(containsString("Edit note")))
        mvc.perform(get(page).alice()).andExpect(content().string(containsString("Remove note")))
        mvc.perform(post(page).alice().with(csrf()).param("body", "").param("format", "MARKDOWN"))
            .andExpect(header().string("HX-Redirect", "/ui/events/$id?saved=noteRemoved"))
        assertNull(eventStore.find(id)!!.notes[occurrence])

        val reminder = eventStore.find(id)!!.reminders[1].id
        mvc.perform(post("/ui/events/$id/send").alice().with(csrf()).param("reminderId", reminder.toString()))
            .andExpect(content().string(containsString("Sent via Club log")))
        mvc.perform(delete("/ui/events/$id").alice().with(csrf())).andExpect(header().string("HX-Redirect", "/ui/events"))
        assertNull(eventStore.find(id))
    }

    @Test
    fun `the REST API manages events and their notes, within the caller's groups`() {
        val log = provision()
        val body = """
            {"group": "notify_club", "title": "Net", "subject": "Net {{event.when}}", "body": "On {{event.frequency}}",
             "schedule": {"kind": "PATTERN", "pattern": "every week on thursday at 8pm", "zone": "America/New_York"},
             "properties": [{"name": "frequency", "value": "146.52"}],
             "reminders": [{"amount": 2, "unit": "HOURS", "timing": "BEFORE", "deliveries": [{"instance": "${log.id}"}]},
                           {"amount": 0, "timing": "AT_START", "deliveries": [{"instance": "${log.id}", "overrides": {"body": "text"}}]}]}
        """.trimIndent()

        val created = mvc.perform(post("/api/events").alice().contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reminders[1].describe").value("at the start"))
            .andExpect(jsonPath("$.reminders[1].deliveries[0].overrides.body").value("text"))
            .andReturn().response.contentAsString
        val id = Regex("\"id\":\"([0-9a-f-]{36})\"").find(created)!!.groupValues[1]
        mvc.perform(get("/api/events/$id").bob()).andExpect(status().isNotFound)
        mvc.perform(get("/api/events/$id/upcoming?count=3").alice()).andExpect(jsonPath("$.length()").value(3))

        val occurrence = events.occurrences(eventStore.find(UUID.fromString(id))!!, 1).single()
        mvc.perform(
            put("/api/events/$id/notes").alice().contentType(MediaType.APPLICATION_JSON)
                .content("""{"occurrence": "$occurrence", "note": "Contest weekend"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notes['$occurrence'].text").value("Contest weekend"))
            .andExpect(jsonPath("$.notes['$occurrence'].format").value("MARKDOWN"))
        val firstReminder = eventStore.find(UUID.fromString(id))!!.reminders[0].id
        mvc.perform(post("/api/events/$id/send?reminderId=$firstReminder").alice().header("X-Requested-With", "test")).andExpect(jsonPath("$[0].delivered").value(true))
        mvc.perform(delete("/api/events/$id").bob().header("X-Requested-With", "test")).andExpect(status().isNotFound)
        // Its instance is in use by the event.
        mvc.perform(delete("/api/instances/${log.id}").alice().header("X-Requested-With", "test")).andExpect(status().isConflict)
    }
}
