package com.yoloc.app.features.learn

/**
 * RN: learn/topics.ts. The offline guide's topics, in reading order. `key` is the route argument.
 * Text lives in strings.xml; disease text comes from the classes table, which holds placeholders
 * marked TODO(verify with agronomist) until reviewed. `icon` is the Feather icon name; Step 5 maps
 * it to a drawable.
 */
enum class LearnTopic(val key: String, val icon: String) {
    Stages("stages", "layers"),
    Photos("photos", "camera"),
    How("how", "cpu"),
    ;

    companion object {
        /** RN: isLearnTopic. Null for an unknown route argument (the screen shows an error). */
        fun fromKey(key: String?): LearnTopic? = entries.firstOrNull { it.key == key }
    }
}
