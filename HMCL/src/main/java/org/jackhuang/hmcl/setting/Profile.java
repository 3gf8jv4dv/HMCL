/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.setting;

import com.google.gson.*;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;

import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLDependencyManager;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.ui.WeakListenerHelper;
import org.jackhuang.hmcl.util.ImmediateObjectProperty;
import org.jackhuang.hmcl.util.ImmediateStringProperty;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 *
 * @author huangyuhui
 */
public final class Profile {
    private final WeakListenerHelper helper = new WeakListenerHelper();
    private final HMCLGameRepository repository;
    private final ModManager modManager;

    private final StringProperty selectedVersion = new SimpleStringProperty();

    public StringProperty selectedVersionProperty() {
        return selectedVersion;
    }

    public String getSelectedVersion() {
        return selectedVersion.get();
    }

    public void setSelectedVersion(String selectedVersion) {
        this.selectedVersion.set(selectedVersion);
    }

    private final ObjectProperty<File> gameDir;

    public ObjectProperty<File> gameDirProperty() {
        return gameDir;
    }

    public File getGameDir() {
        return gameDir.get();
    }

    public void setGameDir(File gameDir) {
        this.gameDir.set(gameDir);
    }

    private final ObjectProperty<VersionSetting> global = new ImmediateObjectProperty<>(this, "global", new VersionSetting());

    public ObjectProperty<VersionSetting> globalProperty() {
        return global;
    }

    public VersionSetting getGlobal() {
        return global.get();
    }

    private void setGlobal(VersionSetting global) {
        if (global == null)
            global = new VersionSetting();
        this.global.set(global);
    }

    private final ImmediateStringProperty name;

    public ImmediateStringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    private BooleanProperty useRelativePath = new SimpleBooleanProperty(this, "useRelativePath", false);

    public BooleanProperty useRelativePathProperty() {
        return useRelativePath;
    }

    public boolean isUseRelativePath() {
        return useRelativePath.get();
    }

    public void setUseRelativePath(boolean useRelativePath) {
        this.useRelativePath.set(useRelativePath);
    }

    public Profile(String name) {
        this(name, new File(".minecraft"));
    }

    public Profile(String name, File initialGameDir) {
        this.name = new ImmediateStringProperty(this, "name", name);
        gameDir = new ImmediateObjectProperty<>(this, "gameDir", initialGameDir);
        repository = new HMCLGameRepository(this, initialGameDir);
        modManager = new ModManager(repository);

        gameDir.addListener((a, b, newValue) -> repository.changeDirectory(newValue));
        selectedVersion.addListener(o -> checkSelectedVersion());
        helper.add(EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> checkSelectedVersion()));
    }

    private void checkSelectedVersion() {
        if (!repository.isLoaded()) return;
        String newValue = selectedVersion.get();
        if (!repository.hasVersion(newValue)) {
            Optional<String> version = repository.getVersions().stream().findFirst().map(Version::getId);
            if (version.isPresent())
                selectedVersion.setValue(version.get());
            else if (StringUtils.isNotBlank(newValue))
                selectedVersion.setValue(null);
        }
    }

    public HMCLGameRepository getRepository() {
        return repository;
    }

    public ModManager getModManager() {
        return modManager;
    }

    public HMCLDependencyManager getDependency() {
        return new HMCLDependencyManager(this, Settings.instance().getDownloadProvider());
    }

    public VersionSetting getVersionSetting(String id) {
        VersionSetting vs = repository.getVersionSetting(id);
        if (vs == null || vs.isUsesGlobal()) {
            getGlobal().setGlobal(true); // always keep global.isGlobal = true
            getGlobal().setUsesGlobal(true);
            return getGlobal();
        } else
            return vs;
    }

    public boolean isVersionGlobal(String id) {
        VersionSetting vs = repository.getVersionSetting(id);
        return vs == null || vs.isUsesGlobal();
    }

    /**
     * Make version use self version settings instead of the global one.
     * @param id the version id.
     * @return specialized version setting, null if given version does not exist.
     */
    public VersionSetting specializeVersionSetting(String id) {
        VersionSetting vs = repository.getVersionSetting(id);
        if (vs == null)
            vs = repository.createVersionSetting(id);
        if (vs == null)
            return null;
        vs.setUsesGlobal(false);
        return vs;
    }

    public void globalizeVersionSetting(String id) {
        VersionSetting vs = repository.getVersionSetting(id);
        if (vs != null)
            vs.setUsesGlobal(true);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("gameDir", getGameDir())
                .append("name", getName())
                .append("useRelativePath", isUseRelativePath())
                .toString();
    }

    public void addPropertyChangedListener(InvalidationListener listener) {
        name.addListener(listener);
        global.addListener(listener);
        gameDir.addListener(listener);
        useRelativePath.addListener(listener);
        global.get().addPropertyChangedListener(listener);
        selectedVersion.addListener(listener);
    }

    public static final class Serializer implements JsonSerializer<Profile>, JsonDeserializer<Profile> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public JsonElement serialize(Profile src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null)
                return JsonNull.INSTANCE;

            JsonObject jsonObject = new JsonObject();
            jsonObject.add("global", context.serialize(src.getGlobal()));
            jsonObject.addProperty("gameDir", src.getGameDir().getPath());
            jsonObject.addProperty("useRelativePath", src.isUseRelativePath());
            jsonObject.addProperty("selectedMinecraftVersion", src.getSelectedVersion());

            return jsonObject;
        }

        @Override
        public Profile deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json == JsonNull.INSTANCE || !(json instanceof JsonObject)) return null;
            JsonObject obj = (JsonObject) json;
            String gameDir = Optional.ofNullable(obj.get("gameDir")).map(JsonElement::getAsString).orElse("");

            Profile profile = new Profile("Default", new File(gameDir));
            profile.setGlobal(context.deserialize(obj.get("global"), VersionSetting.class));
            profile.setSelectedVersion(Optional.ofNullable(obj.get("selectedMinecraftVersion")).map(JsonElement::getAsString).orElse(""));
            profile.setUseRelativePath(Optional.ofNullable(obj.get("useRelativePath")).map(JsonElement::getAsBoolean).orElse(false));
            return profile;
        }

    }
}
