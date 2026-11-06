package com.aidlss.mobile

/** Per-game presets stored by the app. These are configuration profiles, not runtime GPU hooks. */
data class GameProfile(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val settings: Map<String, Int>
)

object GameProfiles {
    val all = listOf(
        GameProfile("gangstar_mirage_city", "Gangstar: Mirage City",
            listOf("gangstar mirage city", "mirage city", "gangstar"),
            mapOf("الحدة" to 48, "الوضوح" to 58, "التباين" to 52, "التشبع" to 54,
                "الأحمر" to 50, "الأخضر" to 51, "الأزرق" to 52, "MSAA" to 2,
                "الترشيح متباين الخواص" to 8, "إزاحة Mipmap" to 50, "جودة ترشيح الإكساءات" to 75)),
        GameProfile("tomb_raider_2013_mobile", "Tomb Raider (2013) Mobile",
            listOf("tomb raider", "tombraider", "lara croft"),
            mapOf("الحدة" to 52, "الوضوح" to 62, "التباين" to 53, "التشبع" to 50,
                "الأحمر" to 50, "الأخضر" to 50, "الأزرق" to 51, "MSAA" to 2,
                "الترشيح متباين الخواص" to 8, "إزاحة Mipmap" to 48, "جودة ترشيح الإكساءات" to 80)),
        GameProfile("nfs_most_wanted_2005", "Need for Speed: Most Wanted (2005)",
            listOf("most wanted 2005", "need for speed", "nfsmw", "nfs mw"),
            mapOf("الحدة" to 45, "الوضوح" to 56, "التباين" to 56, "التشبع" to 55,
                "الأحمر" to 51, "الأخضر" to 50, "الأزرق" to 49, "MSAA" to 2,
                "الترشيح متباين الخواص" to 8, "إزاحة Mipmap" to 50, "جودة ترشيح الإكساءات" to 78))
    )

    fun match(vararg candidates: String?): GameProfile? {
        val text = candidates.filterNotNull().joinToString(" ").lowercase()
            .replace('_', ' ').replace('-', ' ')
        return all.firstOrNull { profile -> profile.keywords.any { keyword -> text.contains(keyword) } }
    }
}
