package ai.crm247.tracker

enum class EventType(val wireValue: String) {
    PAGE_VIEW("page_view"),
    CLICK("click"),
    FORM_SUBMIT("form_submit"),
    DOWNLOAD("download"),
    SCROLL("scroll"),
    TIME_ON_PAGE("time_on_page"),
    EXIT_INTENT("exit_intent"),
    CUSTOM("custom");

    companion object {
        fun normalize(raw: String): String {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.wireValue == normalized }?.wireValue ?: CUSTOM.wireValue
        }
    }
}
