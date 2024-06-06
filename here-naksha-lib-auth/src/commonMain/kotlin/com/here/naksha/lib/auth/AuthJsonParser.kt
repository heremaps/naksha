package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.matrices.UpmMatrix
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix

interface AuthJsonParser {

    fun parseUrm(json: String): UserRightsMatrix

    fun parseArm(json: String): UpmMatrix
}