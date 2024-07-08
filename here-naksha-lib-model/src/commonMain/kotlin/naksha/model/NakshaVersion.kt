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
package naksha.model;

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Just an abstraction for Naksha versioning.
 *
 * @property major             the major version (0-65535).
 * @property minor             the minor version (0-65535).
 * @property revision          the revision (0-65535).
 * @property preReleaseTag     the pre-release tag (alpha, beta, none).
 * @property preReleaseVersion the pre-release version (0-255 or null).
 * @since 2.0.3
 */
@Suppress("ConstPropertyName")
@JsExport
class NakshaVersion(
    var major: Int,
    var minor: Int,
    var revision: Int,
    var preReleaseTag: PreReleaseTag = PreReleaseTag.none,
    var preReleaseVersion: Int = FINAL_PRE_RELEASE_VERSION
) : Comparable<NakshaVersion> {

    @Suppress("OPT_IN_USAGE")
    companion object {
        /**
         * Naksha version constant. The last version compatible with XYZ-Hub.
         */
        const val v0_6 = "0.6.0";

        const val v2_0_0 = "2.0.0";

        const val v2_0_3 = "2.0.3";

        const val v2_0_4 = "2.0.4";

        const val v2_0_5 = "2.0.5";

        const val v2_0_6 = "2.0.6";

        const val v2_0_7 = "2.0.7";

        const val v2_0_8 = "2.0.8";

        const val v2_0_9 = "2.0.9";

        const val v2_0_10 = "2.0.10";

        const val v2_0_11 = "2.0.11";

        const val v2_0_12 = "2.0.12";

        const val v2_0_13 = "2.0.13";

        const val v2_0_14 = "2.0.14";

        const val v2_0_15 = "2.0.15";

        const val v2_0_16 = "2.0.16";

        const val v3_0_0 = "3.0.0";

        const val v3_0_0_alpha_0 = "3.0.0-alpha.0";

        const val v3_0_0_alpha_1 = "3.0.0-alpha.1";

        const val v3_0_0_alpha_2 = "3.0.0-alpha.2";

        const val v3_0_0_alpha_8 = "3.0.0-alpha.8";

        const val v3_0_0_alpha_9 = "3.0.0-alpha.9";

        const val v3_0_0_alpha_10 = "3.0.0-alpha.10";

        const val v3_0_0_alpha_11 = "3.0.0-alpha.11";

        const val v3_0_0_alpha_12 = "3.0.0-alpha.12";

        const val v3_0_0_alpha_13 = "3.0.0-alpha.13";

        const val v3_0_0_alpha_14 = "3.0.0-alpha.14";

        const val v3_0_0_alpha_15 = "3.0.0-alpha.15";

        const val v3_0_0_alpha_16 = "3.0.0-alpha.16";

        const val v3_0_0_beta_1 = "3.0.0-beta.1";

        /**
         * The latest version of the naksha-extension stored in the resources.
         * @since 2.0.5
         */
        @JvmField
        @JsStatic
        val latest = of(v3_0_0_beta_1);

        @JvmStatic
        @JsStatic
        val FINAL_PRE_RELEASE_ENC = 255

        @JvmStatic
        @JsStatic
        val FINAL_PRE_RELEASE_VERSION = 255

        /**
         * Parses the given version string and returns the Naksha version.
         *
         * @param version the version like "2.0.3".
         * @return the Naksha version.
         * @throws NumberFormatException if the given string is no valid version.
         * @since 2.0.3
         */
        @JvmStatic
        @JsStatic
        @Throws(NumberFormatException::class)
        fun of(version: String): NakshaVersion {
            val majorEnd = version.indexOf('.')
            val optionalMinorEnd = version.indexOf('.', majorEnd + 1)
            val minorEnd = if (optionalMinorEnd == -1) version.length else optionalMinorEnd
            val revisionEnd = version.indexOf('-', minorEnd + 1)
            val preReleaseTagEnd = if (revisionEnd == -1) -1 else version.indexOf('.', revisionEnd + 1)
            val major = version.substring(0, majorEnd).toInt()
            val minor = version.substring(majorEnd + 1, minorEnd).toInt()
            val revision = if (optionalMinorEnd == -1) 0 else if (revisionEnd == -1) version.substring(minorEnd + 1)
                .toInt() else version.substring(minorEnd + 1, revisionEnd).toInt()
            val preTagString = if (revisionEnd == -1) null else version.substring(revisionEnd + 1, preReleaseTagEnd)
            val preTag = if (preTagString == null) PreReleaseTag.none else PreReleaseTag.valueOf(preTagString)
            val preVersion =
                if (preReleaseTagEnd == -1) FINAL_PRE_RELEASE_VERSION else version.substring(preReleaseTagEnd + 1)
                    .toInt()
            return NakshaVersion(major, minor, revision, preTag, preVersion)
        }
    }

    enum class PreReleaseTag(val enc: Int) {
        none(FINAL_PRE_RELEASE_ENC),
        alpha(0),
        beta(1);

        companion object {
            fun findByValue(enc: Int): PreReleaseTag {
                for (tag in entries) {
                    if (tag.enc == enc) {
                        return tag
                    }
                }
                throw IllegalArgumentException("Unknown pre-release tag: $enc")
            }
        }
    }

    /**
     * Create a Naksha version from a 64-bit binary encoding.
     * @param enc the 64-bit binary encoding.
     * @since 2.0.3
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    @JsName("fromLong")
    constructor(enc: Long) :
            this(
                ((enc ushr 48) and 0xffffL).toInt(),
                ((enc ushr 32) and 0xffffL).toInt(),
                ((enc ushr 16) and 0xffffL).toInt(),
                PreReleaseTag.findByValue(((enc ushr 8) and 0xffL).toInt()),
                if (((enc ushr 8) and 0xffL).toInt() == FINAL_PRE_RELEASE_ENC)
                    FINAL_PRE_RELEASE_VERSION else ((enc) and 0xffL).toInt()
            )

    /**
     * Create a Naksha version from a multi-platform 64-bit binary encoding.
     * @param enc the multi-platform 64-bit binary encoding.
     * @since 3.0.0
     */
    @JsName("fromBigInt")
    constructor(enc: Int64) : this(enc.toLong())

    private var _long: Long? = null

    /**
     * Return the 64-bit binary encoding of this version.
     * @return the 64-bit binary encoding of this version.
     * @since 2.0.3
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    fun toLong(): Long {
        var l = _long
        if (l == null) {
            l = (((major.toLong() and 0xffffL) shl 48)
                    or ((minor.toLong() and 0xffffL) shl 32)
                    or ((revision.toLong() and 0xffffL) shl 16)
                    or ((preReleaseTag.enc.toLong() and 0xffL) shl 8)
                    or (preReleaseVersion.toLong() and 0xffL))
            _long = l
        }
        return l
    }

    /**
     * Return the 64-bit multi-platform binary encoding of this version.
     * @return the 64-bit multi-platform binary encoding of this version.
     * @since 2.0.3
     */
    fun toInt64(): Int64 = Int64(toLong())

    override fun compareTo(o: NakshaVersion): Int {
        val result = toLong() - o.toLong()
        return if (result < 0) -1 else if (result == 0L) 0 else 1
    }

    override fun hashCode(): Int = (toLong() ushr 16).toInt()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other is NakshaVersion) {
            return toLong() == other.toLong()
        }
        return false
    }

    private var _string: String? = null

    override fun toString(): String {
        var s = _string
        if (s == null) {
            val pre = if (preReleaseTag == PreReleaseTag.none) "" else "-$preReleaseTag.$preReleaseVersion"
            s = "$major.$minor.$revision$pre"
            _string = s
        }
        return s
    }
}