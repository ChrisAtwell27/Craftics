package com.crackedgames.craftics.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarterJsonLoaderTest {

    private static JsonObject reward(String body) {
        return JsonParser.parseString("{" + body + "}").getAsJsonObject();
    }

    @Test
    void parseCount_handlesRangeString() {
        int[] r = BarterJsonLoader.parseCount(reward("\"count\": \"6-12\""));
        assertEquals(6, r[0]);
        assertEquals(12, r[1]);
    }

    @Test
    void parseCount_handlesSingleNumber() {
        int[] r = BarterJsonLoader.parseCount(reward("\"count\": 5"));
        assertEquals(5, r[0]);
        assertEquals(5, r[1]);
    }

    @Test
    void parseCount_handlesSingleNumberAsString() {
        int[] r = BarterJsonLoader.parseCount(reward("\"count\": \"7\""));
        assertEquals(7, r[0]);
        assertEquals(7, r[1]);
    }

    @Test
    void parseCount_defaultsToOneWhenMissing() {
        int[] r = BarterJsonLoader.parseCount(reward("\"weight\": 3"));
        assertEquals(1, r[0]);
        assertEquals(1, r[1]);
    }

    @Test
    void parseCount_defaultsToOneOnGarbage() {
        int[] r = BarterJsonLoader.parseCount(reward("\"count\": \"not-a-number\""));
        assertEquals(1, r[0]);
        assertEquals(1, r[1]);
    }
}
