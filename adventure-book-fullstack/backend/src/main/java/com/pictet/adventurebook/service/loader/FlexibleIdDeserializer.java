package com.pictet.adventurebook.service.loader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Reads a {@link FlexibleId} from either a JSON number or a JSON string. Anything else
 * (an object, a boolean) is treated as a malformed file and fails deserialization loudly,
 * rather than silently producing a nonsense id.
 */
class FlexibleIdDeserializer extends JsonDeserializer<FlexibleId> {

    @Override
    public FlexibleId deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node.isNumber()) {
            return new FlexibleId(node.longValue());
        }
        if (node.isTextual()) {
            return new FlexibleId(Long.parseLong(node.textValue().trim()));
        }
        throw new IOException("Expected a section id as a JSON number or string, got: " + node);
    }
}
