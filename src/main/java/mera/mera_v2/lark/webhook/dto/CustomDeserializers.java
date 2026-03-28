package mera.mera_v2.lark.webhook.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CustomDeserializers {

    /**
     * Deserializer for status_history - can be Array [] or Object {}
     */
    public static class StatusHistoryDeserializer extends JsonDeserializer<List<PancakeOrderResponse.StatusHistory>> {
        @Override
        public List<PancakeOrderResponse.StatusHistory> deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            List<PancakeOrderResponse.StatusHistory> result = new ArrayList<>();

            if (p.currentToken() == JsonToken.START_ARRAY) {
                // Normal case: array of objects
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    PancakeOrderResponse.StatusHistory history = p.readValueAs(PancakeOrderResponse.StatusHistory.class);
                    result.add(history);
                }
            } else if (p.currentToken() == JsonToken.START_OBJECT) {
                // Edge case: object instead of array - ignore it
                // Consume the object
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    p.skipChildren();
                }
            }
            // For null or other tokens, return empty list

            return result;
        }
    }

    /**
     * Deserializer for return_fee - can be Boolean (false/true) or Number
     * - false -> 0
     * - true -> null
     * - number -> keep as is
     */
    public static class ReturnFeeDeserializer extends JsonDeserializer<Double> {
        @Override
        public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();

            if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                return p.getDoubleValue();
            } else if (token == JsonToken.VALUE_TRUE) {
                return null;
            } else if (token == JsonToken.VALUE_FALSE) {
                return 0.0;
            } else if (token == JsonToken.VALUE_NULL) {
                return null;
            }

            // Try parsing as string
            String value = p.getValueAsString();
            if (value != null && !value.isEmpty()) {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    // If it's "false" string
                    if ("false".equalsIgnoreCase(value)) return 0.0;
                    if ("true".equalsIgnoreCase(value)) return null;
                }
            }

            return null;
        }
    }

    /**
     * Deserializer for Integer fields that might come as String or Number
     */
    public static class FlexibleIntegerDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();

            if (token == JsonToken.VALUE_NUMBER_INT) {
                return p.getIntValue();
            } else if (token == JsonToken.VALUE_STRING) {
                String value = p.getValueAsString();
                if (value != null && !value.isEmpty()) {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else if (token == JsonToken.VALUE_NULL) {
                return null;
            }

            return null;
        }
    }

    /**
     * Deserializer for Long fields that might come as String or Number
     */
    public static class FlexibleLongDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();

            if (token == JsonToken.VALUE_NUMBER_INT) {
                return p.getLongValue();
            } else if (token == JsonToken.VALUE_STRING) {
                String value = p.getValueAsString();
                if (value != null && !value.isEmpty()) {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else if (token == JsonToken.VALUE_NULL) {
                return null;
            }

            return null;
        }
    }

    /**
     * Deserializer for Double fields that might come as String or Number
     */
    public static class FlexibleDoubleDeserializer extends JsonDeserializer<Double> {
        @Override
        public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();

            if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                return p.getDoubleValue();
            } else if (token == JsonToken.VALUE_STRING) {
                String value = p.getValueAsString();
                if (value != null && !value.isEmpty()) {
                    try {
                        return Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            } else if (token == JsonToken.VALUE_NULL) {
                return null;
            }

            return null;
        }
    }
}
