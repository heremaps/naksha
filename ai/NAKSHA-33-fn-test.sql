\set ON_ERROR_STOP on
-- Validate the functions installed by naksha.sql without changing retained test data/functions.
-- Run Naksha bootstrap before this script to install the current production definitions.
BEGIN;
DO $test$
DECLARE
    fn bigint;
    stored bigint;
    previous bigint;
    upper_bits bigint;
    routing_byte integer;
BEGIN
    IF "naksha~admin".bswap8(72623859790382856) <> 578437695752307201
        OR "naksha~admin".fn_to_int8(0) <> '-9223372036854775808'::bigint
        OR "naksha~admin".fn_to_int8(128) <> 0 THEN
        RAISE EXCEPTION 'Known conversion vector failed';
    END IF;
    FOREACH upper_bits IN ARRAY ARRAY[0::bigint, '-9223372036854775808'::bigint, -256::bigint] LOOP
        previous := NULL;
        FOR routing_byte IN 0..255 LOOP
            fn := upper_bits | routing_byte::bigint;
            stored := "naksha~admin".fn_to_int8(fn);
            IF "naksha~admin".int8_to_fn(stored) <> fn OR (previous IS NOT NULL AND previous >= stored) THEN
                RAISE EXCEPTION 'Round trip or routing order failed for %', fn;
            END IF;
            previous := stored;
        END LOOP;
    END LOOP;
    FOREACH fn IN ARRAY ARRAY['-9223372036854775808'::bigint, 9223372036854775807::bigint, -1::bigint] LOOP
        IF "naksha~admin".int8_to_fn("naksha~admin".fn_to_int8(fn)) <> fn THEN
            RAISE EXCEPTION 'Extreme round trip failed';
        END IF;
    END LOOP;
    IF "naksha~admin".fn_to_int8(NULL) IS NOT NULL THEN
        RAISE EXCEPTION 'STRICT null handling failed';
    END IF;
    RAISE NOTICE 'PASS: SQL known vectors, signed extremes, all routing bytes and null handling';
END
$test$;
ROLLBACK;
