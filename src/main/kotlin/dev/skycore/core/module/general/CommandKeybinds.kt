package dev.skycore.core.module.general

import dev.skycore.config.SkyCoreConfig
import dev.skycore.core.skyblock.TabListCache
import dev.skycore.core.skyblock.Titles
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import org.lwjgl.glfw.GLFW

object CommandKeybinds {

    enum class Modifier {
        Any, None, Shift, Ctrl, Alt
    }

    private val pressed = HashSet<Int>()

    private val opts get() = SkyCoreConfig.instance.commandKeybinds

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
    }

    fun isActive(): Boolean =
        SkyCoreConfig.instance.enabled && opts.enabled

    fun binds(): MutableList<SkyCoreConfig.CommandBind> = opts.binds

    fun save() = SkyCoreConfig.save()

    fun addBind(): SkyCoreConfig.CommandBind {
        val bind = SkyCoreConfig.CommandBind()
        opts.binds.add(bind)
        save()
        return bind
    }

    fun removeBind(index: Int) {
        if (index !in opts.binds.indices) return
        opts.binds.removeAt(index)
        save()
    }

    private fun tick(client: Minecraft) {
        if (!isActive() || client.player == null) {
            pressed.clear()
            return
        }
        val handle = client.window.handle()
        val modifiers = currentModifiers(handle)
        for (bind in opts.binds) {
            if (!bind.enabled || bind.keyCode < 0 || bind.command.isEmpty()) continue
            if (!screenAllowed(client, bind.allowInGui)) continue
            if (!modifierMatches(bind.modifier, modifiers)) continue
            if (!islandAllowed(bind.islandFilter)) continue
            val down = isKeyDown(handle, bind.keyCode)
            val was = bind.keyCode in pressed
            if (down && !was) {
                Titles.sendChatOrCommand(bind.command)
            }
            if (down) pressed.add(bind.keyCode) else pressed.remove(bind.keyCode)
        }
        pressed.removeAll { code -> !isKeyDown(handle, code) }
    }

    private fun screenAllowed(client: Minecraft, allowBindInGui: Boolean): Boolean {
        val screen = client.gui.screen()
        if (screen == null) return true
        if ((opts.allowAllInGui || allowBindInGui) && screen is AbstractContainerScreen<*>) {
            return screen !is AnvilScreen
        }
        return false
    }

    private fun islandAllowed(filter: String): Boolean {
        if (filter.isBlank()) return true
        val area = TabListCache.area
        if (area.isEmpty()) return true
        val lower = area.lowercase()
        return filter.lowercase().split(Regex("\\s+")).any { it.isNotEmpty() && lower.contains(it) }
    }

    private fun parseModifier(raw: String): Modifier {
        for (value in Modifier.entries) {
            if (value.name.equals(raw, ignoreCase = true)) return value
        }
        return Modifier.Any
    }

    private fun modifierMatches(raw: String, modifiers: Int): Boolean {
        return when (parseModifier(raw)) {
            Modifier.Any -> true
            Modifier.None -> modifiers == 0
            Modifier.Shift -> modifiers == GLFW.GLFW_MOD_SHIFT
            Modifier.Ctrl -> modifiers == GLFW.GLFW_MOD_CONTROL
            Modifier.Alt -> modifiers == GLFW.GLFW_MOD_ALT
        }
    }

    private fun currentModifiers(handle: Long): Int {
        var mods = 0
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
        ) mods = mods or GLFW.GLFW_MOD_SHIFT
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        ) mods = mods or GLFW.GLFW_MOD_CONTROL
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
        ) mods = mods or GLFW.GLFW_MOD_ALT
        return mods
    }

    private fun isKeyDown(handle: Long, keyCode: Int): Boolean {
        if (keyCode in 0..7) {
            return GLFW.glfwGetMouseButton(handle, keyCode) == GLFW.GLFW_PRESS
        }
        return GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS
    }
}
