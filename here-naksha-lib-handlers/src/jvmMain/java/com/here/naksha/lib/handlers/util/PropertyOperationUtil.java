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
package com.here.naksha.lib.handlers.util;

import com.here.naksha.lib.core.lambdas.F1;
import naksha.model.request.RequestQuery;
import naksha.model.request.query.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class PropertyOperationUtil {
    private static final IPropertyQuery AND_NEUTRAL = PTrue.INSTANCE;
    private static final IPropertyQuery OR_NEUTRAL = PFalse.INSTANCE;

    private PropertyOperationUtil() {
    }

    public static Set<PQuery> disablePQueriesInRequest(@NotNull RequestQuery requestQuery, @NotNull F1<Boolean, PQuery> shouldDisable) {
        IPropertyQuery rootPropertyQuery = requestQuery.getProperties();
        if (rootPropertyQuery != null) {
            HashSet<PQuery> disabledProperties = new HashSet<>();
            IPropertyQuery newRootPropertyQuery = disablePropertyInPropertyQueryTree(
                    rootPropertyQuery, shouldDisable, disabledProperties, PTrue.INSTANCE
            )
                    .flatMap(newRoot -> {
                        if (newRoot == PTrue.INSTANCE) {
                            return Optional.empty();
                        }
                        return Optional.of(newRoot);
                    }).orElse(null);
            requestQuery.setProperties(newRootPropertyQuery);
            return disabledProperties;
        }
        // root property query is null -> no disabled property queries -> empty set
        return Collections.emptySet();
    }

    /**
     * @param current            Currently traversed node
     * @param removalCondition   If evaluates to true, it effectively disables the check by replacing it with `true-ish` query
     * @param disabledProperties Set of so-far disabled property queries
     * @param parentsNeutral     The parent's neutral value
     */
    private static Optional<IPropertyQuery> disablePropertyInPropertyQueryTree(
            @NotNull IPropertyQuery current,
            @NotNull F1<Boolean, PQuery> removalCondition,
            @NotNull Set<PQuery> disabledProperties,
            @NotNull IPropertyQuery parentsNeutral
    ) {
        switch (current) {
            case PAnd pAnd -> {
                if (pAnd.stream().allMatch(pq -> pq == AND_NEUTRAL)) {
                    return Optional.of(AND_NEUTRAL);
                }
                List<IPropertyQuery> newChildren = disablePropertyInChildrenQueryTree(
                        pAnd,
                        removalCondition,
                        disabledProperties,
                        AND_NEUTRAL
                ).stream()
                        .filter(pq -> pq != AND_NEUTRAL)
                        .toList();
                pAnd = new PAnd();
                pAnd.addAll(newChildren);
                boolean isAlwaysFalse = pAnd.stream().anyMatch(q -> q == PFalse.INSTANCE);
                if (isAlwaysFalse) {
                    return Optional.of(PFalse.INSTANCE);
                }
                return handleCompoundQuery(pAnd, parentsNeutral);
            }
            case POr pOr -> {
                if (pOr.stream().allMatch(pq -> pq == OR_NEUTRAL)) {
                    return Optional.of(OR_NEUTRAL);
                }
                List<IPropertyQuery> newChildren = disablePropertyInChildrenQueryTree(
                        pOr,
                        removalCondition,
                        disabledProperties,
                        OR_NEUTRAL
                ).stream()
                        .filter(pq -> pq != OR_NEUTRAL)
                        .toList();
                pOr = new POr();
                pOr.addAll(newChildren);
                boolean isAlwaysTrue = pOr.stream().anyMatch(q -> q == PTrue.INSTANCE);
                if (isAlwaysTrue) {
                    return Optional.of(PTrue.INSTANCE);
                }
                return handleCompoundQuery(pOr, parentsNeutral);
            }
            case PNot pNot -> {
                // The pNot neutral is the negated parent's neutral
                IPropertyQuery notNeutral = parentsNeutral == PFalse.INSTANCE ? PTrue.INSTANCE : PFalse.INSTANCE;
                return disablePropertyInPropertyQueryTree(
                        pNot.getQuery(), removalCondition, disabledProperties, notNeutral
                ).flatMap(pq -> {
                    if (pq == PFalse.INSTANCE) {
                        return Optional.of(PTrue.INSTANCE);
                    }
                    if (pq == PTrue.INSTANCE) {
                        return Optional.of(PFalse.INSTANCE);
                    }
                    return Optional.of(new PNot(pq));
                });
            }
            case PQuery currentPQuery when removalCondition.call(currentPQuery) -> {
                disabledProperties.add(currentPQuery);
                return Optional.empty();
            }
            default -> {
                // unhandled type / not matching pQuery => stop traversal without failing
                return Optional.of(current);
            }
        }
    }

    private static <T extends List<IPropertyQuery> & IPropertyQuery> Optional<IPropertyQuery> handleCompoundQuery(
            T compoundQuery,
            IPropertyQuery parentsNeutral
    ) {
        // all the children were removed so should be neutral to parent
        if (compoundQuery.isEmpty()) {
            return Optional.of(parentsNeutral);
        }
        if (compoundQuery.size() == 1) {
            return Optional.of(compoundQuery.getFirst());
        }
        return Optional.of(compoundQuery);
    }

    private static <T extends List<IPropertyQuery> & IPropertyQuery> List<IPropertyQuery> disablePropertyInChildrenQueryTree(
            T compoundQuery,
            F1<Boolean, PQuery> removalCondition,
            Set<PQuery> disabledProperties,
            IPropertyQuery parentsNeutral
    ) {
        return compoundQuery.stream()
                .map(child -> disablePropertyInPropertyQueryTree(
                        child, removalCondition, disabledProperties, parentsNeutral
                ))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
