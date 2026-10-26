package com.tongji.cst3492.ta.model;

public final class DocumentMeta {
    private final String id;
    private final String title;
    private final String fileName;
    private final int pageCount;

    public DocumentMeta(String id, String title, String fileName, int pageCount) {
        this.id = id;
        this.title = title;
        this.fileName = fileName;
        this.pageCount = pageCount;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public int getPageCount() {
        return pageCount;
    }
}
