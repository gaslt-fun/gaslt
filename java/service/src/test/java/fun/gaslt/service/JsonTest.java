package fun.gaslt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void serialisesTypesInInsertionOrder() {
        String s = Json.obj()
                .put("a", "x")
                .put("n", 42)
                .put("b", true)
                .put("nil", null)
                .toString();
        assertEquals("{\"a\":\"x\",\"n\":42,\"b\":true,\"nil\":null}", s);
    }

    @Test
    void escapesSpecialCharacters() {
        String s = Json.obj().put("k", "a\"b\\c\nd").toString();
        assertEquals("{\"k\":\"a\\\"b\\\\c\\nd\"}", s);
    }

    @Test
    void nestsObjects() {
        String s = Json.obj().put("outer", Json.obj().put("inner", 1)).toString();
        assertEquals("{\"outer\":{\"inner\":1}}", s);
    }
}
