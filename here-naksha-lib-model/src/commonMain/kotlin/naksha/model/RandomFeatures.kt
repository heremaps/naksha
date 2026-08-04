@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Id
import naksha.base.Base
import naksha.base.fn.Fn1
import naksha.geo.PointCoord
import naksha.geo.SpPoint
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.math.roundToInt

/**
 * A helper to generate random [NakshaFeature]'s.
 *
 * ## Warning
 * Beware that this class is experimental and subject to change. It's here to generate features as test code.
 * @since 3.0
 */
@JsExport
class RandomFeatures private constructor() {
    companion object RandomFeatures_C {
        /**
         * The key of the first-name property.
         * @since 3.0
         */
        const val FIRST_NAME = "firstName"

        /**
         * The key of the first-name tag.
         * @since 3.0
         */
        const val FIRST_NAME_TAG_PREFIX = "$FIRST_NAME="

        /**
         * The key of the middle-name property.
         * @since 3.0
         */
        const val MIDDLE_NAME = "middleName"

        /**
         * The key of the middle-name tag.
         * @since 3.0
         */
        const val MIDDLE_NAME_TAG_PREFIX = "$MIDDLE_NAME="

        /**
         * The key of the last-name property.
         * @since 3.0
         */
        const val LAST_NAME = "lastName"

        /**
         * The key of the last-name tag.
         * @since 3.0
         */
        const val LAST_NAME_TAG_PREFIX = "$LAST_NAME="

        /**
         * The key of the full-name property.
         * @since 3.0
         */
        const val NAME = "name"

        /**
         * The key of the age property _(a value between `10` and `100`, with much more values close to 10 than to 100)_.
         * @since 3.0
         */
        const val AGE = "age"

        /**
         * The key of the age tag.
         * @since 3.0
         */
        const val AGE_TAG_PREFIX = "$AGE:="

        /**
         * Generates random [NakshaFeature] objects.
         * @param count the amount of random features to generate.
         * @return a list of [NakshaFeature], randomly generated.
         * @since 3.0
         */
        @JsName("randomNakshaFeatures")
        @JsStatic
        @JvmStatic
        fun randomFeatures(count: Int): List<NakshaFeature> = randomFeatures(count) { it }

        /**
         * Generate random features, invoking a mutator method that can modify them, and then shall return cast into some specific desired type.
         * @param count the amount of random features to generate.
         * @param mutator a function called with the random [NakshaFeature], which may mutate the features, and then return it, optionally as different type.
         * @return a list of feature, randomly generated, and mutated.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun <T : NakshaFeature> randomFeatures(count: Int, mutator: Fn1<T, NakshaFeature>): List<T> {
            require(count > 0)
            return (1..count).map {
                val f = randomFeature()
                mutator.call(f)
            }
        }

        /**
         * Creates a new random point feature.
         *
         * This features does have:
         * - A couple _(`0` to `4`)_ arbitrary random tags from the [adverbs] list.
         * - A name with [firstName][FIRST_NAME], [lastName][LAST_NAME], and optional [middleName][MIDDLE_NAME] as well [age][AGE].
         *
         * To allow searching, tags are added with the [first-][FIRST_NAME_TAG_PREFIX], [middle-][MIDDLE_NAME_TAG_PREFIX], and [last-name][LAST_NAME_TAG_PREFIX], as well as the [age][AGE_TAG_PREFIX].
         *
         * @param featureId the feature-id to be set, defaults to [PlatformUtil.randomAtoZ].
         * @param tagPossibility the possibility to add tags, defaults to `33%`.
         * @return a new random feature.
         * @since 3.0
         */
        @JsName("randomNakshaFeature")
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun randomFeature(featureId: Id = Id(), tagPossibility: Double = 0.33): NakshaFeature
            = randomFeature(featureId, tagPossibility) { it }

        /**
         * Creates a new random point feature.
         *
         * This features does have:
         * - A couple _(`0` to `4`)_ arbitrary random tags from the [adverbs] list.
         * - A name with [firstName][FIRST_NAME], [lastName][LAST_NAME], and optional [middleName][MIDDLE_NAME] as well [age][AGE].
         *
         * To allow searching, tags are added with the [first-][FIRST_NAME_TAG_PREFIX], [middle-][MIDDLE_NAME_TAG_PREFIX], and [last-name][LAST_NAME_TAG_PREFIX], as well as the [age][AGE_TAG_PREFIX].
         *
         * @param featureId the feature-id to be set, defaults to [PlatformUtil.randomAtoZ].
         * @param tagPossibility the possibility to add tags, defaults to `33%`.
         * @param mutator a function called with the random [NakshaFeature], which may mutate the features, and then return it, optionally as different type.
         * @return a new random feature.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun <T : NakshaFeature> randomFeature(
            featureId: Id = Id(),
            tagPossibility: Double = 0.33,
            mutator: Fn1<T, NakshaFeature>
        ): T {
            val feature = NakshaFeature(featureId)
            val longitude = (Base.random() * 360 - 180).roundToDecimal(3) // -180.0 to 180.0
            val latitude = (Base.random() * 180 - 90).roundToDecimal(3) // -90 to 90.0
            feature.geometry = SpPoint(PointCoord(longitude, latitude, 0.0))

            val firstName = firstNames[(Base.random() * (firstNames.size - 1)).toInt()]
            val lastName = lastNames[(Base.random() * (lastNames.size - 1)).toInt()]
            val name: String
            val middleName: String?
            if (Base.random() <= 0.1) { // 10% chance of middle name
                middleName = firstNames[(Base.random() * (firstNames.size - 1)).toInt()]
                name = "$firstName $middleName-$lastName"
            } else {
                middleName = null
                name = "$firstName $lastName"
            }
            feature.properties[FIRST_NAME] = firstName
            if (middleName != null) {
                feature.properties[MIDDLE_NAME] = middleName
            }
            feature.properties[LAST_NAME] = lastName
            feature.properties[NAME] = name

            // We want a pyramid like distribution between 5/10 and 95/100.
            var maxAge = 5
            var age: Int
            do {
                maxAge += 5
                age = (Base.random() * 95 + 5).toInt() // first around max-age is 10, next 15 aso.
            } while (age > maxAge)
            feature.properties[AGE] = age

            // x% to get tags
            if (Base.random() <= tagPossibility) {
                val xyz = feature.properties.xyz
                val tags = TagList()
                // We add between 1 and 4 adverb tags.
                for (j in 0..3) {
                    var i = (Base.random() * (adverbs.size - 1)).toInt()
                    while (true) {
                        val tag = adverbs[i]
                        if (!tags.contains(tag)) {
                            tags.add(tag)
                            break
                        }
                        i = (i + 1) % adverbs.size
                    }
                    // 50% chance to continue, therefore:
                    // - 33,0% to get one tag
                    // - 16,7% to get two tags
                    // -  8,3% to get three tags
                    // -  4,1% to get four tags
                    if (Base.random() <= 0.5) { // can be 0 and 1
                        break
                    }
                }
                tags.add("$FIRST_NAME_TAG_PREFIX$firstName")
                if (middleName != null) {
                    tags.add("$MIDDLE_NAME_TAG_PREFIX$middleName")
                }
                tags.add("$LAST_NAME_TAG_PREFIX$lastName")
                tags.add("$AGE_TAG_PREFIX$age")
                xyz.tags = tags
            }
            return mutator.call(feature)
        }

        private fun Double.roundToDecimal(decimals: Int): Double {
            var dotAt = 1.0
            repeat(decimals) { dotAt *= 10 }
            val roundedValue = (this * dotAt).roundToInt()
            return roundedValue / dotAt
        }

        /**
         * Adverbs to be used
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val adverbs: Array<String> = arrayOf(
            "abnormally",
            "absentmindedly",
            "accidentally",
            "acidly",
            "actually",
            "adventurously",
            "afterwards",
            "almost",
            "always",
            "angrily",
            "annually",
            "anxiously",
            "arrogantly",
            "awkwardly",
            "badly",
            "bashfully",
            "beautifully",
            "bitterly",
            "bleakly",
            "blindly",
            "blissfully",
            "boastfully",
            "boldly",
            "bravely",
            "briefly",
            "brightly",
            "briskly",
            "broadly",
            "busily",
            "calmly",
            "carefully",
            "carelessly",
            "cautiously",
            "certainly",
            "cheerfully",
            "clearly",
            "cleverly",
            "closely",
            "coaxingly",
            "colorfully",
            "commonly",
            "continually",
            "coolly",
            "correctly",
            "courageously",
            "crossly",
            "cruelly",
            "curiously",
            "daily",
            "daintily",
            "dearly",
            "deceivingly",
            "deeply",
            "defiantly",
            "deliberately",
            "delightfully",
            "diligently",
            "dimly",
            "doubtfully",
            "dreamily",
            "easily",
            "elegantly",
            "energetically",
            "enormously",
            "enthusiastically",
            "equally",
            "especially",
            "even",
            "evenly",
            "eventually",
            "exactly",
            "excitedly",
            "extremely",
            "fairly",
            "faithfully",
            "famously",
            "far",
            "fast",
            "fatally",
            "ferociously",
            "fervently",
            "fiercely",
            "fondly",
            "foolishly",
            "fortunately",
            "frankly",
            "frantically",
            "freely",
            "frenetically",
            "frightfully",
            "fully",
            "furiously",
            "generally",
            "generously",
            "gently",
            "gladly",
            "gleefully",
            "gracefully",
            "gratefully",
            "greatly",
            "greedily",
            "happily",
            "hastily",
            "healthily",
            "heavily",
            "helpfully",
            "helplessly",
            "highly",
            "honestly",
            "hopelessly",
            "hourly",
            "hungrily",
            "immediately",
            "innocently",
            "inquisitively",
            "instantly",
            "intensely",
            "intently",
            "interestingly",
            "inwardly",
            "irritably",
            "jaggedly",
            "jealously",
            "joshingly",
            "jovially",
            "joyfully",
            "joyously",
            "jubilantly",
            "judgementally",
            "justly",
            "keenly",
            "kiddingly",
            "kindheartedly",
            "kindly",
            "kissingly",
            "knavishly",
            "knottily",
            "knowingly",
            "knowledgeably",
            "kookily",
            "lazily",
            "lightly",
            "likely",
            "limply",
            "lively",
            "loftily",
            "longingly",
            "loosely",
            "loudly",
            "lovingly",
            "loyally",
            "luckily",
            "madly",
            "majestically",
            "meaningfully",
            "mechanically",
            "merrily",
            "miserably",
            "mockingly",
            "monthly",
            "more",
            "mortally",
            "mostly",
            "mysteriously",
            "naturally",
            "nearly",
            "neatly",
            "needily",
            "nervously",
            "never",
            "nicely",
            "noisily",
            "not",
            "obediently",
            "obnoxiously",
            "oddly",
            "offensively",
            "officially",
            "often",
            "only",
            "openly",
            "optimistically",
            "overconfidently",
            "owlishly",
            "painfully",
            "partially",
            "patiently",
            "perfectly",
            "physically",
            "playfully",
            "politely",
            "poorly",
            "positively",
            "potentially",
            "powerfully",
            "promptly",
            "properly",
            "punctually",
            "quaintly",
            "quarrelsomely",
            "queasily",
            "queerly",
            "questionably",
            "questioningly",
            "quickly",
            "quietly",
            "quirkily",
            "quizzically",
            "randomly",
            "rapidly",
            "rarely",
            "readily",
            "really",
            "reassuringly",
            "recklessly",
            "regularly",
            "reluctantly",
            "repeatedly",
            "reproachfully",
            "restfully",
            "righteously",
            "rightfully",
            "rigidly",
            "roughly",
            "rudely",
            "sadly",
            "safely",
            "scarcely",
            "scarily",
            "searchingly",
            "sedately",
            "seemingly",
            "seldom",
            "selfishly",
            "separately",
            "seriously",
            "shakily",
            "shamefully",
            "sharply",
            "sheepishly",
            "shrilly",
            "shyly",
            "silently",
            "sleepily",
            "slowly",
            "smoothly",
            "softly",
            "solemnly",
            "solidly",
            "sometimes",
            "soon",
            "speedily",
            "stealthily",
            "sternly",
            "strictly",
            "successfully",
            "suddenly",
            "surprisingly",
            "suspiciously",
            "sweetly",
            "swiftly",
            "sympathetically",
            "tenderly",
            "tensely",
            "terribly",
            "thankfully",
            "thoroughly",
            "thoughtfully",
            "tightly",
            "tomorrow",
            "too",
            "tremendously",
            "triumphantly",
            "truly",
            "truthfully",
            "ultimately",
            "unabashedly",
            "unaccountably",
            "unbearably",
            "unethically",
            "unexpectedly",
            "unfortunately",
            "unimpressively",
            "unnaturally",
            "unnecessarily",
            "upbeatly",
            "upliftingly",
            "uprightly",
            "upside-down",
            "upwardly",
            "urgently",
            "usefully",
            "uselessly",
            "usually",
            "utterly",
            "vacantly",
            "vaguely",
            "vainly",
            "valiantly",
            "vastly",
            "verbally",
            "very",
            "viciously",
            "victoriously",
            "violently",
            "virtually",
            "vivaciously",
            "voluntarily",
            "warmly",
            "weakly",
            "wearily",
            "well",
            "wetly",
            "wholly",
            "wildly",
            "willfully",
            "wisely",
            "woefully",
            "wonderfully",
            "worriedly",
            "wrongly",
            "yawningly",
            "yearly",
            "yearningly",
            "yesterday",
            "yieldingly",
            "youthfully",
            "zealously",
            "zestfully"
        )

        /**
         * The array with all the possible first- and middle-names.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val firstNames: Array<String> = arrayOf(
            "Alice", "Bob", "Charlie", "Daisy", "Edward", "Fiona", "George", "Hannah",
            "Isaac", "Julia", "Kevin", "Lily", "Matthew", "Nora", "Olivia", "Peter",
            "Quincy", "Rachel", "Simon", "Tina"
        )

        /**
         * The array with all the possible last-names.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val lastNames: Array<String> = arrayOf(
            "Anderson", "Baker", "Clark", "Davis", "Edwards", "Fisher", "Garcia",
            "Hernandez", "Irwin", "Johnson", "King", "Lopez", "Martinez", "Nelson",
            "Owens", "Perez", "Quinn", "Roberts", "Smith", "Taylor"
        )
    }
}
