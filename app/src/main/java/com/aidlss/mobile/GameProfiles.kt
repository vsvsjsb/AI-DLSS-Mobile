package com.aidlss.mobile

/** The first development target is Tomb Raider 2013 only. */
object GameProfiles {
    private val tombRaiderKeywords = listOf("tomb raider", "tombraider", "lara croft", "tombraider2013")

    fun isTombRaider(vararg candidates: String?): Boolean {
        val text = candidates.filterNotNull().joinToString(" ").lowercase()
            .replace('_', ' ').replace('-', ' ')
        return tombRaiderKeywords.any { text.contains(it) }
    }
}
