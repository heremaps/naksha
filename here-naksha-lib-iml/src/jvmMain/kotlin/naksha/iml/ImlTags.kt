package naksha.iml

import naksha.base.illegalArg
import naksha.base.unsupportedOp
import naksha.model.request.ops.*

/**
 * Virtual tag map over the unmodified XYZ array. Regex predicates use PostgreSQL ARE, not Java regex. Numeric conversion is evaluated only for the selected last-written key, never for unrelated tags.
 * @since 3.0
 */
internal class ImlTags(private val parameter: (Any?) -> String) {
  fun compile(op: Op): String {
    if (op.at != "tags") throw unsupportedOp("IML tag map operations require tags")
    return when (op) {
      is TagMapHasKey -> lookup(op.key, "TRUE")
      is TagMapHasAnyOf, is TagMapHasAllOf -> {
        val all = op is TagMapHasAllOf
        val keys = if (op is TagMapHasAllOf) op.tagKeys else (op as TagMapHasAnyOf).tagKeys
        val terms = ArrayList<String>()
        for (key in keys) terms.add(lookup(key ?: throw illegalArg("Null IML tag key"), "TRUE"))
        if (terms.isEmpty()) { if (all) "TRUE" else "FALSE" }
        else "(${terms.joinToString(if (all) " AND " else " OR ")})"
      }
      is TagIsNull -> lookup(op.key, "kind = 'null'")
      is TagEquals -> compare(op.key, op.value, "=")
      is TagGt -> compare(op.key, op.value, ">")
      is TagGte -> compare(op.key, op.value, ">=")
      is TagLt -> compare(op.key, op.value, "<")
      is TagLte -> compare(op.key, op.value, "<=")
      is TagStartsWith -> {
        val prefix = op.value.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        lookup(op.key, "kind = 'string' AND val LIKE ${parameter("$prefix%")} ESCAPE '!'")
      }
      is TagMatches -> lookup(op.key, "kind = 'string' AND val ~ ${parameter(op.regex)}")
      else -> throw unsupportedOp("Unsupported IML tag operation '${op.op}'")
    }
  }

  private fun compare(key: String, value: Any?, operator: String): String {
    val test = when (value) {
      null -> "kind = 'null'"
      is String -> "kind = 'string' AND val $operator ${parameter(value)}"
      is Boolean -> {
        if (operator != "=") throw illegalArg("IML boolean tag ranges are not supported")
        "kind = 'boolean' AND val = ${parameter(value.toString())}"
      }
      is Number -> {
        if (!value.toDouble().isFinite()) throw illegalArg("IML numeric tag operands must be finite")
        "CASE WHEN kind = 'number' THEN ($number) $operator ${parameter(value.toDouble())} ELSE FALSE END"
      }
      else -> throw illegalArg("IML tag values must be scalar")
    }
    return lookup(key, test)
  }

  private fun lookup(key: String, test: String): String {
    val placeholder = parameter(key)
    // test parameters were registered before the key; keep that same order in SQL.
    return "COALESCE((WITH parsed AS ($entries) SELECT ($test) FROM " +
      "(SELECT * FROM parsed WHERE key = $placeholder ORDER BY ord DESC LIMIT 1) last_tag), FALSE)"
  }

  companion object {
    private const val source = "jsondata->'properties'->'@ns:com:here:xyz'->'tags'"
    // Kotlin trim uses Unicode whitespace (including NBSP), unlike SQL trim's default ASCII space.
    private val whitespace = (listOf(9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 160, 5760) +
      (8192..8202) + listOf(8232, 8233, 8239, 8287, 12288)).joinToString(" || ") { "chr($it)" }
    private fun trim(sql: String) = "btrim($sql, $whitespace)"
    private const val decimal = "^[+-]?(([0123456789]+([.][0123456789]*)?|[.][0123456789]+)([eE][+-]?[0123456789]+)?)[fFdD]?$"
    private const val hex = "^[+-]?0[xX]([0123456789abcdefABCDEF]+([.][0123456789abcdefABCDEF]*)?|[.][0123456789abcdefABCDEF]+)[pP][+-]?[0123456789]+[fFdD]?$"
    private val doubleWhitespace = (1..32).joinToString(" || ") { "chr($it)" }
    private val halfMinimum = java.math.BigDecimal(Double.MIN_VALUE).divide(java.math.BigDecimal(2)).toPlainString()
    private val overflow = java.math.BigDecimal(2).pow(1024).subtract(java.math.BigDecimal(2).pow(970)).toPlainString()
    // Normalize exponents before numeric casts. PostgreSQL rejects overflow/underflow that JVM Double parses as infinity/zero.
    // Exact decimal thresholds and binary-to-decimal powers preserve rounding at the smallest subnormal and overflow boundaries.
    private val number = """
      SELECT CASE WHEN numeric_raw IN ('NaN','+NaN','-NaN') THEN NULL::float8
        WHEN numeric_raw IN ('Infinity','+Infinity') THEN 'Infinity'::float8
        WHEN numeric_raw = '-Infinity' THEN '-Infinity'::float8
        ELSE CASE WHEN abs(decimal_value) >= $overflow::numeric
          THEN CASE WHEN left(numeric_raw, 1) = '-' THEN '-Infinity'::float8 ELSE 'Infinity'::float8 END
          WHEN abs(decimal_value) <= $halfMinimum::numeric THEN 0::float8
          ELSE decimal_value::float8 END END
      FROM (SELECT *, CASE WHEN numeric_raw ~ '$hex' THEN
        CASE WHEN digits = '' OR decimal_order < -1200 THEN 0::numeric
          WHEN decimal_order > 1200 THEN CASE WHEN left(numeric_raw, 1) = '-' THEN -1e401::numeric ELSE 1e401::numeric END
          ELSE (SELECT sum((strpos('0123456789abcdef', substring(digits FROM n FOR 1)) - 1) *
            CASE WHEN decimal_order - 4 * (n - 1) >= 0 THEN power(2::numeric, decimal_order - 4 * (n - 1))
              ELSE power(5::numeric, -(decimal_order - 4 * (n - 1))) * ('1e' || (decimal_order - 4 * (n - 1)))::numeric END)
            FROM generate_series(1, length(digits)) AS digit(n)) *
            CASE WHEN left(numeric_raw, 1) = '-' THEN -1 ELSE 1 END END
        WHEN numeric_raw ~ '$decimal' THEN
        CASE WHEN digits = '' THEN 0::numeric
          WHEN decimal_order > 400 THEN CASE WHEN left(numeric_raw, 1) = '-' THEN -1e401::numeric ELSE 1e401::numeric END
          WHEN decimal_order < -400 THEN 0::numeric
          ELSE (CASE WHEN left(numeric_raw, 1) = '-' THEN '-' ELSE '' END || '0.' || digits || 'e' || (decimal_order + 1))::numeric END
        END AS decimal_value
        FROM (SELECT *, exponent_value + (CASE WHEN numeric_raw ~ '$hex' THEN 4 ELSE 1 END) *
          (length(split_part(mantissa, '.', 1)) - (length(replace(mantissa, '.', '')) - length(digits)) - 1) AS decimal_order
          FROM (SELECT *, ltrim(replace(mantissa, '.', ''), '0') AS digits,
            CASE WHEN length(exponent_digits) > 6 THEN CASE WHEN left(exponent_text, 1) = '-' THEN -1000000 ELSE 1000000 END
              ELSE COALESCE(NULLIF(exponent_text, ''), '0')::int END AS exponent_value
            FROM (SELECT *, ltrim(ltrim(exponent_text, '+-'), '0') AS exponent_digits
              FROM (SELECT numeric_raw,
                CASE WHEN numeric_raw ~ '$hex' THEN regexp_replace(split_part(lower(numeric_raw), 'p', 1), '^[+-]?0x', '')
                  ELSE regexp_replace(split_part(lower(regexp_replace(numeric_raw, '[fFdD]$', '')), 'e', 1), '^[+-]', '') END AS mantissa,
                CASE WHEN numeric_raw ~ '$hex' THEN substring(numeric_raw FROM '[pP]([+-]?[0123456789]+)')
                  ELSE COALESCE(substring(numeric_raw FROM '[eE]([+-]?[0123456789]+)'), '') END AS exponent_text
                FROM (SELECT btrim(val, $doubleWhitespace) AS numeric_raw) trimmed
              ) parts
            ) exponents
          ) normalized
        ) ordered
      ) converted
    """.trimIndent()

    /** splitTag key/type translation, including invalid := fallback and duplicate-key ordinality. @since 3.0 */
    private val entries = """
      SELECT CASE WHEN NOT split THEN tag WHEN typed AND kind = 'string' THEN tag
        WHEN typed THEN ${trim("left(tag, pos - 2)")} ELSE ${trim("left(tag, pos - 1)")} END AS key,
        kind, CASE WHEN kind = 'boolean' THEN lower(raw) ELSE raw END AS val, ord
      FROM (
        SELECT *, CASE WHEN NOT split THEN 'null' WHEN NOT typed THEN 'string'
          WHEN lower(raw) IN ('true','false') THEN 'boolean'
          WHEN btrim(raw, $doubleWhitespace) ~ '$decimal' OR btrim(raw, $doubleWhitespace) ~ '$hex'
            OR btrim(raw, $doubleWhitespace) IN ('NaN','+NaN','-NaN','Infinity','+Infinity','-Infinity') THEN 'number'
          ELSE 'string' END AS kind
        FROM (
            SELECT *, split AND substring(tag FROM pos - 1 FOR 1) = ':' AS typed,
              ${trim("substring(tag FROM pos + 1)")} AS raw
            FROM (
              SELECT *, pos > 1 AND left(tag, 4) <> 'ref_' AND left(tag, 9) <> 'sourceID_'
                AND left(tag, 14) <> 'xyz_source_id_' AS split
              FROM (
                SELECT item #>> '{}' AS tag, ord,
                  length(item #>> '{}') - strpos(reverse(item #>> '{}'), '=') + 1 AS pos
                FROM jsonb_array_elements(CASE WHEN jsonb_typeof($source) = 'array' THEN $source ELSE '[]'::jsonb END)
                  WITH ORDINALITY AS tags(item, ord)
                WHERE jsonb_typeof(item) = 'string' AND strpos(item #>> '{}', '=') > 0
              ) assigned
            ) positioned
        ) split_tags
      ) typed_tags
      UNION ALL
      SELECT item #>> '{}', 'null', NULL, ord
      FROM jsonb_array_elements(CASE WHEN jsonb_typeof($source) = 'array' THEN $source ELSE '[]'::jsonb END)
        WITH ORDINALITY AS tags(item, ord)
      WHERE jsonb_typeof(item) = 'string' AND strpos(item #>> '{}', '=') = 0
    """.trimIndent()
  }
}
