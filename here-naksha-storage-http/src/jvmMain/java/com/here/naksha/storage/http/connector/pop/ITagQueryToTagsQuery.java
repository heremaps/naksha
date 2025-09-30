package com.here.naksha.storage.http.connector.pop;

import com.here.naksha.lib.core.models.payload.events.TagList;
import com.here.naksha.lib.core.models.payload.events.TagsQuery;
import naksha.model.request.query.ITagQuery;
import naksha.model.request.query.TagAnd;
import naksha.model.request.query.TagExists;
import naksha.model.request.query.TagOr;
import naksha.model.request.query.TagQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class ITagQueryToTagsQuery {
    private ITagQueryToTagsQuery() {}

    public static TagsQuery toTagsQuery(ITagQuery root) {
        if (root == null) throw new IllegalArgumentException("ITagQuery is null");
        ensureExistsOnly(root);

        List<LinkedHashSet<String>> dnf = toDNF(root);

        if (dnf.isEmpty()) throw new IllegalArgumentException("Empty tag query");
        TagsQuery out = new TagsQuery();
        for (LinkedHashSet<String> conj : dnf) {
            if (conj.isEmpty()) throw new IllegalArgumentException("AND with no TagExists()");
            TagList tl = new TagList();
            conj.forEach(tl::add);
            out.add(tl);
        }
        return out;
    }


    private static List<LinkedHashSet<String>> toDNF(ITagQuery q) {
        if (q instanceof TagExists) {
            String name = ((TagQuery) q).getName();
            LinkedHashSet<String> conj = new LinkedHashSet<>();
            conj.add(name);
            return List.of(conj);
        }

        if (q instanceof TagAnd) {
            List<ITagQuery> kids = children(q);
            if (kids.isEmpty()) throw new IllegalArgumentException("AND must have children");
            List<LinkedHashSet<String>> acc = new ArrayList<>();
            acc.add(new LinkedHashSet<>());

            for (ITagQuery child : kids) {
                List<LinkedHashSet<String>> childDnf = toDNF(child);
                List<LinkedHashSet<String>> next = new ArrayList<>();
                for (LinkedHashSet<String> left : acc) {
                    for (LinkedHashSet<String> right : childDnf) {
                        LinkedHashSet<String> merged = new LinkedHashSet<>(left);
                        merged.addAll(right);
                        next.add(merged);
                    }
                }
                acc = next;
            }
            return dedupe(acc);
        }

        if (q instanceof TagOr) {
            List<ITagQuery> kids = children(q);
            if (kids.isEmpty()) throw new IllegalArgumentException("OR must have children");
            List<LinkedHashSet<String>> res = new ArrayList<>();
            for (ITagQuery child : kids) {
                res.addAll(toDNF(child));
            }
            return dedupe(res);
        }

        throw new IllegalArgumentException("Unsupported tag node: " + q.getClass().getSimpleName());
    }

    private static List<LinkedHashSet<String>> dedupe(List<LinkedHashSet<String>> items) {
        LinkedHashSet<LinkedHashSet<String>> set = new LinkedHashSet<>(items);
        return new ArrayList<>(set);
    }

    private static void ensureExistsOnly(ITagQuery q) {
        if (q instanceof TagExists || q instanceof TagAnd || q instanceof TagOr) {
            for (ITagQuery c : maybeChildren(q)) ensureExistsOnly(c);
            return;
        }
        throw new IllegalArgumentException("Value/regex/NOT tag queries cannot be sent to Hub TagsQuery: "
                + q.getClass().getSimpleName());
    }


   @SuppressWarnings("unchecked")
    private static List<ITagQuery> children(ITagQuery listNode) {
        return (List<ITagQuery>) listNode;
    }

    private static List<ITagQuery> maybeChildren(ITagQuery node) {
        if (node instanceof TagAnd || node instanceof TagOr) return children(node);
        return Collections.emptyList();
    }
}
