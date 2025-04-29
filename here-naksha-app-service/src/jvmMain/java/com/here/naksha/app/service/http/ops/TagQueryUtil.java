package com.here.naksha.app.service.http.ops;

import static com.here.naksha.common.http.apis.ApiParamsConst.TAGS;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.AMPERSAND;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.COMMA;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.END;
import static com.here.naksha.lib.core.models.payload.events.QueryDelimiter.PLUS;
import static naksha.model.TagNormalizer.normalizeTag;

import com.here.naksha.lib.core.models.payload.events.QueryDelimiter;
import com.here.naksha.lib.core.models.payload.events.QueryParameter;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.util.ValueList;
import java.util.List;
import java.util.Set;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.request.query.ITagQuery;
import naksha.model.request.query.TagAnd;
import naksha.model.request.query.TagExists;
import naksha.model.request.query.TagOr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TagQueryUtil {

  private TagQueryUtil() {
  }

  private static final Set<QueryDelimiter> ALLOWED_DELIMITERS = Set.of(
      AMPERSAND, END, COMMA, PLUS
  );

  public static @Nullable ITagQuery tagQueryFromParams(final @Nullable QueryParameterList queryParams) {
    if (queryParams == null) {
      return null;
    }
    QueryParameter tagParams = queryParams.get(TAGS);
    if (tagParams == null) {
      return null;
    }
    TagOr rootTagOrQuery = new TagOr();
    while (tagParams.hasValues()) {
      processParams(rootTagOrQuery, tagParams);
      tagParams = tagParams.next();
    }

    if(rootTagOrQuery.size() == 1){
      return rootTagOrQuery.get(0);
    }
    return rootTagOrQuery;
  }

  private static void processParams(
      final @NotNull TagOr rootOrQuery,
      final @NotNull QueryParameter tagParams
  ) {
    final ValueList tagTokens = tagParams.values();
    final List<QueryDelimiter> delimiters = tagParams.valuesDelimiter();
    int delimIdx = 0;
    ITagQuery currentSubQuery = null;
    for (final Object token : tagTokens) {
      String tag = (String) token;
      if (tag == null || tag.isEmpty()) {
        if (currentSubQuery == null) { // we skip null/empty value if it is at the start of operation
          delimIdx++;
          continue;
        } else { // null/empty value in middle of AND/OR operation not allowed
          throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Empty tag not allowed - " + tagTokens);
        }
      }
      final QueryDelimiter delimiter = delimiters.get(delimIdx++);
      if (!ALLOWED_DELIMITERS.contains(delimiter)) {
        throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid delimiter " + delimiter + " for parameter " + TAGS);
      }
      currentSubQuery = processSubQuery(rootOrQuery, currentSubQuery, delimiter, tag);
    }
  }

  private static ITagQuery processSubQuery(
      final @NotNull TagOr rootOrQuery,
      final @Nullable ITagQuery currentSubQuery,
      final @NotNull QueryDelimiter currentDelimiter,
      final @NotNull String tag
  ) {
    if (currentSubQuery == null) {
      return processNewSubQuery(rootOrQuery, currentDelimiter, tag);
    } else if (currentSubQuery instanceof TagOr currentTagSubQuery) {
      return processOrSubQuery(rootOrQuery, currentTagSubQuery, currentDelimiter, tag);
    } else if (currentSubQuery instanceof TagAnd currentAndSubQuery) {
      return processAndSubQuery(rootOrQuery, currentAndSubQuery, currentDelimiter, tag);
    } else {
      throw new NakshaException(NakshaError.ILLEGAL_STATE, "Unsupported subquery type: " + currentSubQuery.getClass().getName());
    }
  }

  private static ITagQuery processNewSubQuery(
      TagOr rootOrQuery,
      QueryDelimiter delimiter,
      String tag
  ) {
    if (delimiter == AMPERSAND || delimiter == END) {
      // this is the only tag, add directly to root
      rootOrQuery.add(tagExists(tag));
    } else if (delimiter == COMMA) {
      // open new OR operation with current tag
      return new TagOr(tagExists(tag));
    } else if (delimiter == PLUS) {
      // open new AND operation with current tag
      return new TagAnd(tagExists(tag));
    }
    throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Could not process new subQuery for delimiter: " + delimiter);
  }

  private static ITagQuery processOrSubQuery(
      TagOr rootOrQuery,
      TagOr currentSubQuery,
      QueryDelimiter delimiter,
      String tag
  ){
    if (delimiter == AMPERSAND || delimiter == END) {
      // closing OR - add current tag, populate root, return null as we closed current subQuery
      currentSubQuery.add(tagExists(tag));
      rootOrQuery.add(currentSubQuery);
      return null;
    } else if (delimiter == COMMA) {
      // continuing population of OR subQuery - just add new tag to it
      currentSubQuery.add(tagExists(tag));
      return currentSubQuery;
    } else if (delimiter == PLUS) {
      // change of operation sequence, closing OR, opening AND and treating it as current subQuery
      rootOrQuery.add(currentSubQuery);
      TagAnd newAndSubQuery = new TagAnd();
      newAndSubQuery.add(tagExists(tag));
      return newAndSubQuery;
    }
    throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Could not process OR subQuery for delimiter: " + delimiter);
  }

  private static ITagQuery processAndSubQuery(
      TagOr rootOrQuery,
      TagAnd currentSubQuery,
      QueryDelimiter delimiter,
      String tag
  ){
    if (delimiter == AMPERSAND || delimiter == END || delimiter == COMMA) {
      // closing AND - add current tag, populate root, return null as we closed current subQuery
      currentSubQuery.add(tagExists(tag));
      rootOrQuery.add(currentSubQuery);
      return null;
    } else if (delimiter == PLUS) {
      // continuing population of AND subQuery - just add new tag to it
      currentSubQuery.add(tagExists(tag));
    }
    throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Could not process AND subQuery for delimiter: " + delimiter);
  }

  private static TagExists tagExists(String tag) {
    return new TagExists(normalizeTag(tag));
  }
}
