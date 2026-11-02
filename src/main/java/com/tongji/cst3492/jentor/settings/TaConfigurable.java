package com.tongji.cst3492.jentor.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;

public final class TaConfigurable implements Configurable {
    private final TaSettings settings = TaSettings.getInstance();

    private final JBTextField baseUrl = new JBTextField();
    private final JBTextField chatModel = new JBTextField();
    private final JPasswordField apiKey = new JPasswordField();
    private final JBTextField topK = new JBTextField();
    private final JBTextField coverageThreshold = new JBTextField();
    private final JBTextField maxContextChars = new JBTextField();
    private final JBTextField temperature = new JBTextField();
    private final JBTextField maxTokens = new JBTextField();
    private final JComboBox<String> language =
            new JComboBox<>(new String[]{"中文", "English"});
    private final JCheckBox useMockModel =
            new JCheckBox("Use offline mock model (no API key) / 使用离线 Mock 模型（无需 API Key）");
    private final JCheckBox streamOutput =
            new JCheckBox("Stream the answer while it is generated / 流式输出回答（边生成边显示）");

    @Override
    public String getDisplayName() {
        return "Jentor";
    }

    @Override
    public String getHelpTopic() {
        return null;
    }

    @Override
    public @Nullable JComponent createComponent() {
        return FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("<html><b>Connection / 连接</b></html>"))
                .addLabeledComponent(new JBLabel("Base URL / 接口地址:"), baseUrl, 1, false)
                .addLabeledComponent(new JBLabel("Chat model / 对话模型:"), chatModel, 1, false)
                .addLabeledComponent(new JBLabel("API key / API 密钥:"), apiKey, 1, false)
                .addComponent(new JBLabel("<html><b>Answer & language / 回答与语言</b></html>"))
                .addLabeledComponent(new JBLabel("Language / 语言:"), language, 1, false)
                .addLabeledComponent(new JBLabel(
                        "Max output tokens / 最大输出 token 数:"), maxTokens, 1, false)
                .addComponent(new JBLabel("<html><b>Course library / 课程资料库</b></html>"))
                .addComponent(new JBLabel(
                        "<html><body style='width:520px'>"
                                + "Course PDFs are managed in the Jentor tool window's "
                                + "<b>Course materials</b> tab. PDFs may come from any folder and "
                                + "can be added, relocated, rebuilt or removed independently."
                                + "<br/><br/>"
                                + "课程 PDF 在 Jentor 工具窗口的“课程资料”页中管理。资料可以来自任意目录，"
                                + "并能独立添加、定位、重建或移除。"
                                + "</body></html>"))
                .addComponent(new JBLabel("<html><b>Advanced / 高级设置</b></html>"))
                .addLabeledComponent(new JBLabel("Top-K chunks / 检索片段数:"), topK, 1, false)
                .addLabeledComponent(new JBLabel(
                        "Coverage threshold / 覆盖率阈值:"), coverageThreshold, 1, false)
                .addLabeledComponent(new JBLabel(
                        "Max context chars / 最大上下文字符数:"), maxContextChars, 1, false)
                .addLabeledComponent(new JBLabel("Temperature / 温度:"), temperature, 1, false)
                .addComponent(useMockModel)
                .addComponent(streamOutput)
                .addComponent(new JBLabel(
                        "<html><body style='width:420px'>"
                                + "Use an OpenAI-compatible endpoint. The API key is stored in "
                                + "IntelliJ's credential store, not in the project. "
                                + "Coverage threshold 0.45 means the top retrieved chunk must "
                                + "share at least 45% of the question's IDF-weighted terms."
                                + "<br/><br/>"
                                + "使用兼容 OpenAI 的接口。API Key 保存在 IntelliJ 凭据库中，"
                                + "不会写入项目。覆盖率阈值 0.45 表示最高分检索片段必须覆盖"
                                + "问题中至少 45% 的 IDF 加权词项。"
                                + "</body></html>"))
                .addComponentFillVertically(new JBLabel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        TaSettings.State state = settings.getState();
        return !baseUrl.getText().equals(state.baseUrl)
                || !chatModel.getText().equals(state.chatModel)
                || !topK.getText().equals(String.valueOf(state.topK))
                || !coverageThreshold.getText().equals(String.valueOf(state.coverageThreshold))
                || !maxContextChars.getText().equals(String.valueOf(state.maxContextChars))
                || !temperature.getText().equals(String.valueOf(state.temperature))
                || !maxTokens.getText().equals(String.valueOf(state.maxTokens))
                || language.getSelectedIndex() != ("zh".equals(state.language) ? 0 : 1)
                || useMockModel.isSelected() != state.useMockModel
                || streamOutput.isSelected() != state.streamOutput
                || !new String(apiKey.getPassword()).equals(nullToEmpty(settings.getApiKey()));
    }

    @Override
    public void apply() throws ConfigurationException {
        TaSettings.State state = settings.getState();
        state.baseUrl = requireText(baseUrl, "Base URL");
        state.chatModel = requireText(chatModel, "Chat model");
        state.topK = parseInt(topK, "Top-K chunks", 1, 20);
        state.coverageThreshold = parseDouble(coverageThreshold, "Coverage threshold", 0.0, 1.0);
        state.maxContextChars = parseInt(maxContextChars, "Max context chars", 1000, 20000);
        state.temperature = parseDouble(temperature, "Temperature", 0.0, 2.0);
        state.maxTokens = parseInt(maxTokens, "Max output tokens", 128, 8000);
        state.language = language.getSelectedIndex() == 0 ? "zh" : "en";
        state.useMockModel = useMockModel.isSelected();
        state.streamOutput = streamOutput.isSelected();
        settings.setApiKey(new String(apiKey.getPassword()).trim());
    }

    @Override
    public void reset() {
        TaSettings.State state = settings.getState();
        baseUrl.setText(state.baseUrl);
        chatModel.setText(state.chatModel);
        apiKey.setText(nullToEmpty(settings.getApiKey()));
        topK.setText(String.valueOf(state.topK));
        coverageThreshold.setText(String.valueOf(state.coverageThreshold));
        maxContextChars.setText(String.valueOf(state.maxContextChars));
        temperature.setText(String.valueOf(state.temperature));
        maxTokens.setText(String.valueOf(state.maxTokens));
        language.setSelectedIndex("zh".equals(state.language) ? 0 : 1);
        useMockModel.setSelected(state.useMockModel);
        streamOutput.setSelected(state.streamOutput);
    }

    @Override
    public void disposeUIResources() {
        // Swing components are released with the settings dialog.
    }

    private String requireText(JBTextField field, String name) throws ConfigurationException {
        String value = field.getText().trim();
        if (value.isEmpty()) {
            throw new ConfigurationException(name + " must not be empty.");
        }
        return value;
    }

    private int parseInt(JBTextField field, String name, int min, int max)
            throws ConfigurationException {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ConfigurationException(name + " must be an integer between "
                    + min + " and " + max + ".");
        }
    }

    private double parseDouble(JBTextField field, String name, double min, double max)
            throws ConfigurationException {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ConfigurationException(name + " must be a number between "
                    + min + " and " + max + ".");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

}
