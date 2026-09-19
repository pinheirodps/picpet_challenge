package com.pictet.adventurebook.service.loader;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A section id or {@code gotoId} as found in the source JSON, which is inconsistent about
 * whether these are numbers or strings — {@code crystal-caverns.json} uses {@code "id": 1},
 * {@code the-prisoner.json} uses {@code "id": "500"}. This wrapper accepts either shape and
 * always exposes a plain {@code long}, so the rest of the loader doesn't need to care which
 * one a given file used.
 */
@JsonDeserialize(using = FlexibleIdDeserializer.class)
public record FlexibleId(long value) {
}
