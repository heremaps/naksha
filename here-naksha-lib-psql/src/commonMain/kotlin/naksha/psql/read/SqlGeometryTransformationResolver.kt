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
package naksha.psql.read

import naksha.model.request.query.SpTransformation


class SqlGeometryTransformationResolver {
    fun wrapWithTransformation(
        transformation: SpTransformation?, variablePlaceholder: String
    ): StringBuilder {
        var variableSql = StringBuilder(variablePlaceholder)
        if (transformation == null) {
            return variableSql
        }
        if (transformation.childTransformation != null) {
            variableSql = wrapWithTransformation(transformation.childTransformation, variablePlaceholder)
        }
        val sqlCond = StringBuilder()
        // TODO: Fix me !!!
//        when (transformation) {
//            is SpBuffer -> {
//                val bufferT: SpBuffer = transformation
//                sqlCond.append(" ST_Buffer(")
//                    .append(variableSql)
//                    .append(",")
//                    .append(bufferT.distance)
//                    .append(",")
//                    .append(PgUtil.quoteLiteral(bufferT.getProperties()))
//                    .append(") ")
//            }
//
//            is GeographyTransformation -> {
//                sqlCond.append(variableSql).append("::geography ")
//            }
//
//            else -> {
//                TODO("add missing transformation")
//            }
//        }
        return sqlCond
    }
}
