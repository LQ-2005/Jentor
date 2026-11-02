package com.tongji.cst3492.jentor.model;

public final class Chunk {
    private final String id;
    private final String docId;
    private final String docTitle;
    private final String fileName;
    private final String locatorType;
    private final int pageStart;
    private final int pageEnd;
    private final int chunkIndex;
    private final String title;
    private final String text;
    private final float[] vector;

    public Chunk(
            String id,
            String docId,
            String docTitle,
            String fileName,
            String locatorType,
            int pageStart,
            int pageEnd,
            int chunkIndex,
            String title,
            String text,
            float[] vector) {
        this.id = id;
        this.docId = docId;
        this.docTitle = docTitle;
        this.fileName = fileName;
        this.locatorType = locatorType;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.chunkIndex = chunkIndex;
        this.title = title;
        this.text = text;
        this.vector = vector;
    }

    public String getId() {
        return id;
    }

    public String getDocId() {
        return docId;
    }

    public String getDocTitle() {
        return docTitle;
    }

    public String getFileName() {
        return fileName;
    }

    public String getLocatorType() {
        return locatorType;
    }

    public int getPageStart() {
        return pageStart;
    }

    public int getPageEnd() {
        return pageEnd;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public float[] getVector() {
        return vector;
    }

    public String locator() {
        String singular;
        String plural;
        switch (locatorType == null ? "PAGE" : locatorType.toUpperCase()) {
            case "SLIDE":
                singular = "slide ";
                plural = "slides ";
                break;
            case "SECTION":
                singular = "section ";
                plural = "sections ";
                break;
            case "SHEET":
                singular = "sheet ";
                plural = "sheets ";
                break;
            case "IMAGE":
                singular = "image ";
                plural = "images ";
                break;
            case "PAGE":
            default:
                singular = "p.";
                plural = "pp.";
                break;
        }
        return pageStart == pageEnd
                ? singular + pageStart
                : plural + pageStart + "-" + pageEnd;
    }
}
