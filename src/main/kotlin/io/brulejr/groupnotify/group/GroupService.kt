/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.group

import io.brulejr.groupnotify.security.NotifyUser

/**
 * Who may see and manage which group's notifications.
 *
 * A notification belongs to exactly one group. Its members see it; an admin sees every
 * group's. Managing it, creating, editing, pausing, sending, also needs the user to be
 * an editor (see `notify.security.editor-group`).
 */
class GroupService(private val datafill: GroupDatafill) {

    private val pattern = Regex(datafill.pattern)

    /** Whether [group] may hold notifications at all. */
    fun isNotificationGroup(group: String) = NAME.matches(group) && pattern.matches(group) && group !in datafill.exclude

    /** The notification groups [user] belongs to. */
    fun memberGroups(user: NotifyUser): List<String> = user.groups.filter(::isNotificationGroup).sorted()

    fun canView(user: NotifyUser, group: String) = user.admin || user.memberOf(group)

    fun canEdit(user: NotifyUser, group: String) = user.editor && canView(user, group)

    /**
     * The groups whose notifications [user] may see, or null for every group: what the
     * stores are queried with.
     */
    fun visibilityFilter(user: NotifyUser): Set<String>? = if (user.admin) null else user.groups

    /** Groups [user] may see: their own, and for an admin also every group [inUse]. */
    fun viewable(user: NotifyUser, inUse: Collection<String>): List<String> =
        if (user.admin) (memberGroups(user) + inUse).distinct().sorted() else memberGroups(user)

    /** Groups [user] may create a notification in. An admin may also name any other. */
    fun editable(user: NotifyUser, inUse: Collection<String>): List<String> =
        if (!user.editor) emptyList() else viewable(user, inUse).filter(::isNotificationGroup)

    fun displayName(group: String): String = datafill.names[group] ?: group

    /** Every group's display name, for templates. */
    fun displayNames(): Map<String, String> = HashMap(datafill.names)

    /** Why [user] cannot put a notification in [group], or null if they can. */
    fun problemWith(user: NotifyUser, group: String): String? = when {
        group.isBlank() -> "must be chosen"
        !isNotificationGroup(group) -> "is not a notification group"
        !user.editor -> "you may not manage notifications"
        !user.admin && !user.memberOf(group) -> "you are not a member of it"
        else -> null
    }

    private companion object {
        /** LLDAP group names: what can travel in a comma-separated header. */
        val NAME = Regex("[A-Za-z0-9][A-Za-z0-9 _.@-]*")
    }
}
