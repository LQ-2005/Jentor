package com.tongji.cst3492.jentor.ui;

public final class I18n {
    public static final String EN = "en";
    public static final String ZH = "zh";

    private I18n() {
    }

    public static String t(String language, String key) {
        boolean zh = ZH.equals(language);
        return switch (key) {
            case "welcome" -> zh
                    ? "我是 Jentor，你的 CST3492 Java Enterprise 助教。"
                    + "可以问我课件里的问题，我会在引用课程材料时标出文档名和页码。"
                    + "选中代码后右键 “Ask Jentor about Selection”，可以把代码附加到下一次提问。"
                    : "I am Jentor, your CST3492 Java Enterprise teaching assistant. "
                    + "Ask a question about the slides and I will cite the source document "
                    + "and page when I use course material. Select code in the editor and "
                    + "right-click \"Ask Jentor about Selection\" to attach it to your next question.";
            case "index" -> zh ? "索引" : "Index";
            case "loadIndex" -> zh ? "加载索引" : "Load index";
            case "clear" -> zh ? "清空" : "Clear";
            case "settings" -> zh ? "设置" : "Settings";
            case "send" -> zh ? "发送" : "Send";
            case "stop" -> zh ? "停止生成" : "Stop";
            case "thinking" -> zh ? "正在思考..." : "Thinking...";
            case "you" -> zh ? "你" : "You";
            case "courseTa" -> zh ? "Jentor 助教" : "Jentor";
            case "error" -> zh ? "错误" : "Error";
            case "contextAttached" -> zh ? "已附加代码：" : "Context attached: ";
            case "chars" -> zh ? " 字符" : " chars";
            case "pages" -> zh ? " 页" : " pages";
            case "sources" -> zh ? "来源：" : "Sources: ";
            case "sourcesCount" -> zh ? "引用来源" : "Sources";
            case "debugCandidates" -> zh ? "检索候选（调试）" : "Retrieval candidates (debug)";
            case "copyAnswer" -> zh ? "复制回答" : "Copy answer";
            case "view" -> zh ? "查看" : "View";
            case "remove" -> zh ? "移除" : "Remove";
            case "attachmentTitle" -> zh ? "代码附件" : "Code attachment";
            case "newReply" -> zh ? "有新回复" : "New reply";
            case "retry" -> zh ? "重试" : "Retry";
            case "openSettings" -> zh ? "打开设置" : "Open settings";
            case "importIndexAction" -> zh ? "导入索引" : "Import index";
            case "stopped" -> zh ? "已停止生成" : "Generation stopped";
            case "contextSnapshot" -> zh
                    ? "发送时的代码快照（已按上限截断）"
                    : "Code snapshot sent (truncated to the limit)";
            case "citationsValidated" -> zh
                    ? "文档与页码已校验"
                    : "Document and page validated";
            case "citationsFailed" -> zh
                    ? "引用未通过校验"
                    : "Citation validation failed";
            case "lowCoverage" -> zh
                    ? "本次未检索到足够相关的课程片段"
                    : "No sufficiently relevant course excerpts were retrieved";
            case "footnotePrefix" -> zh ? "注：" : "Note: ";
            case "attachedFiles" -> zh ? "个文件" : "files";
            case "askInWorkspace" -> zh ? "在本工作区中询问" : "Ask in this workspace";
            case "courseMaterialsReady" -> zh ? "课程资料就绪 · " : "Course materials ready · ";
            case "documents" -> zh ? " 份课件" : " documents";
            case "mockBadge" -> zh ? "离线演示 / Mock" : "Mock mode";
            case "courseMaterials" -> zh ? "课程资料" : "Course materials";
            case "courseMaterialsIndexing" -> zh
                    ? "正在解析并索引课程 PDF…"
                    : "Parsing and indexing course PDFs...";
            case "courseMaterialsWaiting" -> zh
                    ? "课程资料库等待首次索引"
                    : "Course library is waiting for its first index";
            case "importIndex" -> zh ? "导入课程索引" : "Import course index";
            case "clearConversation" -> zh ? "清空会话" : "Clear conversation";
            case "warning" -> zh ? "提示：" : "Warning: ";
            case "indexStatus" -> zh ? "索引：" : "Index: ";
            case "indexError" -> zh ? "索引错误：" : "Index error: ";
            case "loadIndexTitle" -> zh ? "选择 course-index.json" : "Select course-index.json";
            case "cannotLoadIndex" -> zh ? "无法加载课程索引" : "Cannot load course index";
            case "indexDialogTitle" -> zh ? "Jentor 课程索引" : "Jentor Course Index";
            case "loadedIndex" -> zh ? "已加载新的课程索引：" : "Loaded a new course index: ";
            case "language" -> zh ? "语言" : "Language";
            case "sendHint" -> zh
                    ? "Enter 发送，Shift+Enter 换行"
                    : "Enter sends, Shift+Enter adds a new line";
            default -> key;
        };
    }
}
