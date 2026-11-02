package com.tongji.cst3492.jentor.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.cst3492.jentor.model.CourseLibraryCatalog;

import java.io.IOException;

/** JSON serialization for the independently managed course PDF catalog. */
public final class CourseLibraryCatalogCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CourseLibraryCatalogCodec() {
    }

    public static byte[] toJsonBytes(CourseLibraryCatalog catalog) throws IOException {
        return MAPPER.writeValueAsBytes(catalog);
    }

    public static CourseLibraryCatalog fromJson(byte[] json) {
        if (json == null || json.length == 0) {
            return CourseLibraryCatalog.empty();
        }
        try {
            CourseLibraryCatalog catalog = MAPPER.readValue(json, CourseLibraryCatalog.class);
            return catalog == null ? CourseLibraryCatalog.empty() : catalog;
        } catch (IOException error) {
            return CourseLibraryCatalog.empty();
        }
    }
}
