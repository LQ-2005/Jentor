package com.tongji.cst3492.ta.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.cst3492.ta.model.WorkspaceIndex;

import java.io.IOException;

/** JSON form of the workspace index, used for the on-disk cache. */
public final class WorkspaceIndexCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkspaceIndexCodec() {
    }

    public static String toJson(WorkspaceIndex index) {
        try {
            return MAPPER.writeValueAsString(index);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot encode the workspace index", error);
        }
    }

    /** @return the decoded index, or {@code null} when the payload is not a valid index. */
    public static WorkspaceIndex fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            WorkspaceIndex index = MAPPER.readValue(json, WorkspaceIndex.class);
            return index == null ? null : index;
        } catch (IOException error) {
            return null;
        }
    }
}
