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
            case "routingRequest" -> zh ? "正在理解请求..." : "Understanding your request...";
            case "you" -> zh ? "你" : "You";
            case "courseTa" -> zh ? "Jentor 助教" : "Jentor";
            case "error" -> zh ? "错误" : "Error";
            case "contextAttached" -> zh ? "已附加代码：" : "Context attached: ";
            case "chars" -> zh ? " 字符" : " chars";
            case "pages" -> zh ? " 页" : " pages";
            case "sources" -> zh ? "来源：" : "Sources: ";
            case "sourcesCount" -> zh ? "引用来源" : "Sources";
            case "view" -> zh ? "查看" : "View";
            case "remove" -> zh ? "移除" : "Remove";
            case "attachmentTitle" -> zh ? "代码附件" : "Code attachment";
            case "newReply" -> zh ? "有新回复" : "New reply";
            case "history" -> zh ? "历史" : "History";
            case "newConversation" -> zh ? "新对话" : "New chat";
            case "loadingConversation" -> zh ? "正在加载对话…" : "Loading conversation...";
            case "conversationLoadFailed" -> zh
                    ? "无法加载该对话" : "Cannot load the conversation";
            case "conversationSaveFailed" -> zh
                    ? "对话保存失败" : "Cannot save the conversation";
            case "stopBeforeConversationChange" -> zh
                    ? "当前回答仍在生成，继续将停止生成并切换对话。"
                    : "An answer is still being generated. Continuing will stop it and switch conversations.";
            case "conversationChangeTitle" -> zh
                    ? "切换对话" : "Switch conversation";
            case "renameConversation" -> zh ? "重命名对话" : "Rename conversation";
            case "renameConversationPrompt" -> zh
                    ? "输入新的对话名称：" : "Enter a new conversation title:";
            case "deleteConversation" -> zh ? "删除对话" : "Delete conversation";
            case "deleteConversationPrompt" -> zh
                    ? "删除这条历史对话？课程资料不会被删除。"
                    : "Delete this conversation? Course materials will not be deleted.";
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
            case "askInWorkspaceHint" -> zh
                    ? "每次提问时附加项目结构和相关代码摘录"
                    : "Include the project structure and relevant code excerpts";
            case "mockMode" -> "Mock";
            case "onlineMode" -> zh ? "在线模型" : "Online model";
            case "onlineModeHint" -> zh
                    ? "使用设置中配置的在线 API 模型"
                    : "Use the configured online API model";
            case "mockModeHint" -> zh
                    ? "使用离线演示模型，不发送网络请求"
                    : "Use the offline demo model without network requests";
            case "courseMaterials" -> zh ? "课程资料" : "Course materials";
            case "courseMaterialsIndexing" -> zh
                    ? "正在解析并索引课程 PDF…"
                    : "Parsing and indexing course PDFs...";
            case "courseMaterialsWaiting" -> zh
                    ? "课程资料库等待首次索引"
                    : "Course library is waiting for its first index";
            case "importIndex" -> zh ? "导入课程索引" : "Import course index";
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
            case "editPrompt" -> zh
                    ? "可以直接描述希望如何修改当前代码。"
                    : "Describe how you want the current code changed.";
            case "editNoTarget" -> zh
                    ? "我还不能确定要修改哪个文件。请打开目标文件，或告诉我类名或方法名。"
                    : "I cannot determine which file to change. Open the target or name the class or method.";
            case "editRelocated" -> zh
                    ? "我定位到实际需要修改的文件是"
                    : "I located the file that actually needs the change:";
            case "editReadOnly" -> zh
                    ? "目标文件为只读，无法应用修改："
                    : "The target file is read-only and cannot be edited:";
            case "editGenerating" -> zh ? "正在生成修改建议…" : "Generating a suggested change...";
            case "editApplied" -> zh ? "已应用代码修改：" : "Code edit applied:";
            case "editCancelled" -> zh
                    ? "已取消代码修改，源码保持不变。"
                    : "Code edit cancelled; source files were not changed.";
            case "editStatusApplied" -> zh
                    ? "最近一次已应用的代码修改：" : "Last applied code edit:";
            case "editStatusNone" -> zh
                    ? "当前对话中还没有成功应用过代码修改。"
                    : "No code edit has been applied successfully in this conversation.";
            case "intentClarify" -> zh
                    ? "我还不确定你是希望解释、分析还是修改代码。可以再具体说明吗？"
                    : "I am not yet sure whether you want an explanation, analysis, or a code change. Could you clarify?";
            case "editNoChange" -> zh
                    ? "当前文件已满足该要求（NO_CHANGE）"
                    : "The file already satisfies the request (NO_CHANGE)";
            case "editRefused" -> zh ? "模型拒绝修改：" : "The model refused the change: ";
            case "editReviewTitle" -> zh ? "Jentor：审阅修改建议" : "Jentor: review suggested change";
            case "editReviewCurrent" -> zh ? "当前文件" : "Current";
            case "editReviewSuggested" -> zh ? "建议内容" : "Suggested";
            case "editApply" -> zh ? "应用" : "Apply";
            case "editApplyAll" -> zh ? "应用全部" : "Apply all";
            case "editReviewHint" -> zh
                    ? "左侧会随文件修改实时刷新；确认后点击“应用”写入编辑器（可 Ctrl+Z 撤销）。"
                    : "The left side follows the file live; press Apply to write it (Ctrl+Z undoes it).";
            case "editBatchHint" -> zh
                    ? "每个标签页都使用 IntelliJ 内置双栏 Diff；确认后点击“应用全部”（可 Ctrl+Z 撤销）。"
                    : "Each tab uses IntelliJ's built-in two-pane diff; press Apply all after review (Ctrl+Z undoes it).";
            case "editBatchStale" -> zh
                    ? "至少一个源文件在审阅期间发生变化，整批修改已禁用，请取消后重新生成。"
                    : "At least one source changed during review. The batch is disabled; cancel and regenerate.";
            case "editBatchInvalid" -> zh
                    ? "至少一个文件不可审阅或存在 Java 语法错误，无法应用整批修改。"
                    : "At least one file cannot be reviewed or contains a Java syntax error. The batch cannot be applied.";
            case "editSkippedFiles" -> zh ? "已跳过：" : "Skipped:";
            case "editStale" -> zh
                    ? "源文件在审阅期间被修改，建议可能已过期，请取消后重新生成。"
                    : "The source changed while reviewing; the suggestion may be stale. Cancel and regenerate.";
            case "editSyntaxError" -> zh
                    ? "建议的 Java 代码存在语法错误，无法应用："
                    : "The suggested Java file has syntax errors and cannot be applied:";
            default -> key;
        };
    }
}
