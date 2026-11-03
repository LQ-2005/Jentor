package com.tongji.cst3492.jentor.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "JentorSettings",
        storages = @Storage("jentor.xml"))
public final class TaSettings implements PersistentStateComponent<TaSettings.State> {
    private static final CredentialAttributes API_KEY_CREDENTIALS =
            new CredentialAttributes("JentorApiKey");

    private State state = new State();

    public static TaSettings getInstance() {
        return ApplicationManager.getApplication().getService(TaSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public @Nullable String getApiKey() {
        return PasswordSafe.getInstance().getPassword(API_KEY_CREDENTIALS);
    }

    public void setApiKey(@Nullable String apiKey) {
        PasswordSafe.getInstance().setPassword(API_KEY_CREDENTIALS, apiKey);
    }

    public static final class State {
        public String baseUrl = "https://api.deepseek.com";
        public String chatModel = "deepseek-chat";
        public String language = "zh";
        public int topK = 5;
        public double coverageThreshold = 0.45;
        public int maxContextChars = 6000;
        public double temperature = 0.2;
        public int maxTokens = 1500;
        public boolean useMockModel = false;
        public boolean streamOutput = true;
        public boolean workspaceContext = false;
        public boolean editWorkspace = false;
        /** Legacy 1.1 folder, consumed once when migrating to the 1.2 catalog. */
        public String courseMaterialsPath = "";
    }
}
