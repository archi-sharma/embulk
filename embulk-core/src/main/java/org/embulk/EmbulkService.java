package org.embulk;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.embulk.config.ConfigSource;

// Use EmbulkEmbed instead. To be removed by v0.10 or earlier.
@Deprecated  // https://github.com/embulk/embulk/issues/932
public class EmbulkService {
    private final ConfigSource systemConfig;

    protected Injector injector;
    private boolean initialized;

    @Deprecated
    public EmbulkService(ConfigSource systemConfig) {
        this.systemConfig = systemConfig;
    }

    @Deprecated
    protected Iterable<? extends Module> getAdditionalModules(ConfigSource systemConfig) {
        return Collections.unmodifiableList(new ArrayList<Module>());
    }

    @Deprecated
    protected Iterable<? extends Module> overrideModules(Iterable<? extends Module> modules, ConfigSource systemConfig) {
        return modules;
    }

    @Deprecated
    static List<Module> standardModuleList(ConfigSource systemConfig) {
        return EmbulkEmbed.standardModuleList(systemConfig);
    }

    @Deprecated
    public Injector initialize() {
        if (this.initialized) {
            throw new IllegalStateException("Already initialized");
        }

        final ArrayList<Module> modulesBuilt = new ArrayList<>();
        modulesBuilt.addAll(standardModuleList(systemConfig));
        for (final Module module : getAdditionalModules(systemConfig)) {
            modulesBuilt.add(module);
        }

        Iterable<? extends Module> modules = Collections.unmodifiableList(modulesBuilt);
        modules = overrideModules(modules, systemConfig);

        injector = Guice.createInjector(modules);
        initialized = true;

        return injector;
    }

    @Deprecated
    public synchronized Injector getInjector() {
        if (initialized) {
            return injector;
        }
        return initialize();
    }
}
