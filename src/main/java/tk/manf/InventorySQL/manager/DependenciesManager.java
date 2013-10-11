/**
 * Copyright (c) 2013 Exo-Network
 * 
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 * 
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 * 
 *    1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 
 *    2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 
 *    3. This notice may not be removed or altered from any source
 *    distribution.
 * 
 * manf                   info@manf.tk
 */

package tk.manf.InventorySQL.manager;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.List;
import lombok.Getter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public final class DependenciesManager implements Listener {
    private JavaPlugin plugin;
    private ClassLoader loader;

    private DependenciesManager() {
    }

    public void initialise(JavaPlugin plugin, ClassLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        checkDatabaseHandler();
    }

    @EventHandler
    public void onPluginEnable(final PluginEnableEvent ev) {
        switch (scanPlugin(ev.getPlugin())){
            case PARENT:
                hookPlugin(ev.getPlugin());
                return;
            case CHILD:
                checkDatabaseHandler();
                return;
            case NONE:
                // This plugins does not like us nor do we :(
                return;
            default:
                throw new IllegalArgumentException("Unknown DependencyType!");
        }
    }

    private void checkDatabaseHandler() {
        if (findDatabaseHandler()) {
            try {
                DatabaseManager.getInstance().reload(plugin, loader);
                loader = null;
                plugin = null;
            } catch (Exception ex) {
                LoggingManager.getInstance().log(ex);
            }
        }
    }

    private boolean findDatabaseHandler() {
        if (loader == null) {
            return false;
        }
        try {
            Method method = loader.getClass().getDeclaredMethod("findLoadedClass", new Class<?>[]{String.class});
            method.setAccessible(true);
            return method.invoke(loader, ConfigManager.getInstance().getDatabaseHandler()) != null;
        } catch (ReflectiveOperationException ex) {
            // Does not have our handler yet
        } catch (RuntimeException ex) {
            LoggingManager.getInstance().log(ex);
        }
        return false;
    }

    private void hookPlugin(Plugin p) {
        LoggingManager.getInstance().log(LoggingManager.Level.DEVELOPER, "Hooked into " + p.getName() + " (" + p.getDescription().getVersion() + ")");
        // Uhm we do not have any dependency yet, so we don't need any further steps
    }

    private DependencyType scanPlugin(Plugin p) {
        if (isDependency(p.getName())) {
            return DependencyType.PARENT;
        }
        if (getDependencies(p.getDescription()).contains(plugin.getDescription().getName())) {
            return DependencyType.CHILD;
        }
        return DependencyType.NONE;
    }

    private boolean isDependency(String name) {
        return plugin.getDescription().getDepend().contains(name) || plugin.getDescription().getSoftDepend().contains(name);
    }

    private List<String> getDependencies(PluginDescriptionFile pdf) {
        return new ImmutableList.Builder<String>().addAll(pdf.getSoftDepend()).addAll(pdf.getDepend()).build();
    }

    @Getter
    private static final DependenciesManager instance = new DependenciesManager();

    private enum DependencyType {
        NONE, CHILD, PARENT
    }
}