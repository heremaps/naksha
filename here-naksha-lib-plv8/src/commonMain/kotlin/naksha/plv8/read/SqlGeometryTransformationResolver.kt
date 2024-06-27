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
package naksha.plv8.read

import naksha.model.request.condition.geometry.BufferTransformation
import naksha.model.request.condition.geometry.GeographyTransformation
import naksha.model.request.condition.geometry.GeometryTransformation
import naksha.plv8.PgSession
import naksha.plv8.PgUtil


class SqlGeometryTransformationResolver(
    val sql: PgSession
) {
    fun wrapWithTransformation(
        transformation: GeometryTransformation?, variablePlaceholder: String
    ): StringBuilder {
        var variableSql = StringBuilder(variablePlaceholder)
        if (transformation == null) {
            return variableSql
        }
        if (transformation.hasChildTransformation()) {
            variableSql = wrapWithTransformation(transformation.childTransformation, variablePlaceholder)
        }
        val sqlCond = StringBuilder()
        when (transformation) {
            is BufferTransformation -> {
                val bufferT: BufferTransformation = transformation
                sqlCond.append(" ST_Buffer(")
                    .append(variableSql)
                    .append(",")
                    .append(bufferT.distance)
                    .append(",")
                    .append(PgUtil.quoteLiteral(bufferT.getProperties()))
                    .append(") ")
            }

            is GeographyTransformation -> {
                sqlCond.append(variableSql).append("::geography ")
            }

            else -> {
                TODO("add missing transformation")
            }
        }
        return sqlCond
    }
}
