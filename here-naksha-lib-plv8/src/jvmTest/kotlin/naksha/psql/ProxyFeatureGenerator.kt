/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package naksha.psql

import naksha.geo.PointProxy
import naksha.model.NakshaFeatureProxy
import naksha.model.TagsProxy
import org.apache.commons.lang3.RandomStringUtils
import java.util.concurrent.ThreadLocalRandom

/**
 * A helper class to generate random [NakshaFeatureProxy]'s.
 */
class ProxyFeatureGenerator {
    var adverbs: Array<String> = arrayOf(
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

    var firstNames: Array<String> = arrayOf(
        "Alice", "Bob", "Charlie", "Daisy", "Edward", "Fiona", "George", "Hannah",
        "Isaac", "Julia", "Kevin", "Lily", "Matthew", "Nora", "Olivia", "Peter",
        "Quincy", "Rachel", "Simon", "Tina"
    )

    var lastNames: Array<String> = arrayOf(
        "Anderson", "Baker", "Clark", "Davis", "Edwards", "Fisher", "Garcia",
        "Hernandez", "Irwin", "Johnson", "King", "Lopez", "Martinez", "Nelson",
        "Owens", "Perez", "Quinn", "Roberts", "Smith", "Taylor"
    )

    /**
     * Creates a new random point feature with a couple (0 to 4) of arbitrary random tags from the [.adverbs] list, a name with
     * firstName, lastName and optional middleName as well as an age. To allow searching for names, we add tags with the first, middle and
     * last names as well as the age.
     *
     * @return A new random feature.
     */
    fun newRandomFeature(): NakshaFeatureProxy {
        val rand = ThreadLocalRandom.current()
        val featureId = RandomStringUtils.randomAlphanumeric(20)
        val feature = NakshaFeatureProxy(featureId)
        val longitude = rand.nextDouble(-180.0, +180.0)
        val latitude = rand.nextDouble(-90.0, +90.0)
        feature.geometry = PointProxy(longitude, latitude)

        val firstName = firstNames[rand.nextInt(0, firstNames.size)]
        val lastName = lastNames[rand.nextInt(0, lastNames.size)]
        val name: String
        val middleName: String?
        if (rand.nextInt(0, 10) == 0) { // can be 0 .. 9, so 10% chance of middle name
            middleName = firstNames[rand.nextInt(0, firstNames.size)]
            name = "$firstName $middleName-$lastName"
        } else {
            middleName = null
            name = "$firstName $lastName"
        }
        feature.nakshaProperties().put("firstName", firstName)
        if (middleName != null) {
            feature.nakshaProperties().put("middleName", middleName)
        }
        feature.nakshaProperties().put("lastName", lastName)
        feature.nakshaProperties().put("name", name)

        // We want a pyramid like distribution between 5/10 and 95/100.
        var maxAge = 5
        var age: Int
        do {
            maxAge += 5
            age = rand.nextInt(5, 100) // first around max-age is 10, next 15 aso.
        } while (age > maxAge)
        feature.nakshaProperties().put("age", age)

        // 33% to get tags
        if (rand.nextInt(3) == 0) { // can be 0, 1 and 2
            val xyz = feature.xyz()!!
            val tags = TagsProxy()
            // We add between 1 and 4 tags.
            for (j in 0..3) {
                var i = rand.nextInt(0, adverbs.size)
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
                if (rand.nextInt(0, 2) == 0) { // can be 0 and 1
                    break
                }
            }
            tags.add("@:firstName:$firstName")
            if (middleName != null) {
                tags.add("@:middleName:$middleName")
            }
            tags.add("@:lastName:$lastName")
            tags.add("@:age:$age")
            xyz.tags = tags
        }
        return feature
    }
}
