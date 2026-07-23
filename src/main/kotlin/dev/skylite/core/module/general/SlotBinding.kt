package dev.skylite.core.module.general

import com.mojang.blaze3d.platform.InputConstants
import dev.skylite.config.SkyLiteConfig
import dev.skylite.core.skyblock.Titles
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW

object SlotBinding {

    private const val BINDING_COLOR = 0xFF00FF00.toInt()
    private const val BOUND_COLOR = 0xFF00E5FF.toInt()

    lateinit var slotBindingKey: KeyMapping
        private set

    private var lastSlotId: Int = -1
    private var keyWasDown: Boolean = false

    private val opts get() = SkyLiteConfig.instance.slotBinding

    fun init() {
        slotBindingKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.skylite.slot_binding",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                KeyMapping.Category.MISC
            )
        )
        applyKeybindFromConfig()
    }

    fun isActive(): Boolean =
        SkyLiteConfig.instance.enabled && opts.enabled

    fun clearAll() {
        opts.binds.clear()
        SkyLiteConfig.save()
    }

    fun bindsSnapshot(): Map<String, List<Int>> =
        opts.binds.mapValues { (_, v) -> v.binds.toList() }

    fun applyKeybindFromConfig() {
        if (!::slotBindingKey.isInitialized) return
        val saved = opts.keybindName
        if (saved.isEmpty()) return
        val key = runCatching { InputConstants.getKey(saved) }.getOrNull() ?: return
        if (key != KeyMappingHelper.getBoundKeyOf(slotBindingKey)) {
            slotBindingKey.setKey(key)
            KeyMapping.resetMapping()
        }
    }

    fun captureKeybind() {
        if (!::slotBindingKey.isInitialized) return
        val current = slotBindingKey.saveString()
        if (opts.keybindName != current) {
            opts.keybindName = current
            SkyLiteConfig.save()
        }
    }

    fun onRender(context: GuiGraphicsExtractor, menu: AbstractContainerMenu, hovered: Slot?) {
        if (!isActive()) return
        val client = Minecraft.getInstance()
        if (client.gui.screen() !is InventoryScreen) return
        if (hovered == null) {
            if (lastSlotId >= 0) drawBorder(context, menu, lastSlotId, BINDING_COLOR)
            return
        }
        val focused = hovered.index
        if (isHotbar(focused) && hasData(focused)) {
            for (id in hotbarBind(focused).binds) {
                drawBorder(context, menu, id, BOUND_COLOR)
            }
        } else if (isValid(focused)) {
            for (hb in 1..8) {
                val name = "hotbar$hb"
                val data = opts.binds[name] ?: continue
                if (focused in data.binds) {
                    drawBorder(context, menu, hb + 35, BOUND_COLOR)
                }
            }
        }
        if (lastSlotId >= 0) {
            drawBorder(context, menu, lastSlotId, BINDING_COLOR)
            drawBorder(context, menu, focused, BINDING_COLOR)
        }
    }

    fun onMouseClick(slot: Slot?, slotId: Int, button: Int, actionType: ContainerInput): Boolean {
        if (!isActive()) return false
        val client = Minecraft.getInstance()
        val screen = client.gui.screen()
        if (screen !is InventoryScreen) return false
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        if (!client.hasShiftDown()) return false
        val focusedId = slot?.index ?: slotId
        if (!isValid(focusedId)) return false
        val syncId = screen.menu.containerId
        val gameMode = client.gameMode ?: return false
        val player = client.player ?: return false

        if (isHotbar(focusedId) && hasData(focusedId)) {
            val data = hotbarBind(focusedId)
            val target = when {
                data.last != 0 -> data.last
                data.binds.isNotEmpty() -> data.binds[0]
                else -> 0
            }
            if (target != 0) {
                gameMode.handleContainerInput(syncId, target, toHotbar(focusedId) - 1, ContainerInput.SWAP, player)
                return true
            }
        } else {
            for (hb in 1..8) {
                val data = opts.binds["hotbar$hb"] ?: continue
                if (focusedId in data.binds) {
                    gameMode.handleContainerInput(syncId, focusedId, hb - 1, ContainerInput.SWAP, player)
                    data.last = focusedId
                    SkyLiteConfig.save()
                    return true
                }
            }
        }
        return false
    }

    fun onKey(key: Int, action: Int, hovered: Slot?): Boolean {
        if (!isActive()) return false
        val client = Minecraft.getInstance()
        if (client.gui.screen() !is InventoryScreen) return false
        if (!::slotBindingKey.isInitialized) return false
        val bound = KeyMappingHelper.getBoundKeyOf(slotBindingKey)
        if (bound.value != key) return false

        val focusedId = hovered?.index ?: -1
        if (action == GLFW.GLFW_PRESS) {
            if (isValid(focusedId)) lastSlotId = focusedId
            keyWasDown = true
            return true
        }
        if (action == GLFW.GLFW_RELEASE) {
            keyWasDown = false
            handleRelease(focusedId)
            lastSlotId = -1
            return true
        }
        return false
    }

    fun tickKeyState(hovered: Slot?) {
        if (!isActive() || !::slotBindingKey.isInitialized) return
        val client = Minecraft.getInstance()
        if (client.gui.screen() !is InventoryScreen) {
            if (keyWasDown) {
                keyWasDown = false
                lastSlotId = -1
            }
            return
        }
        val down = slotBindingKey.isDown
        if (down && !keyWasDown) {
            val id = hovered?.index ?: -1
            if (isValid(id)) lastSlotId = id
        } else if (!down && keyWasDown) {
            handleRelease(hovered?.index ?: -1)
            lastSlotId = -1
        }
        keyWasDown = down
        captureKeybind()
    }

    fun keyReady(): Boolean = ::slotBindingKey.isInitialized

    private fun handleRelease(focusedId: Int) {
        if (isValid(focusedId) && lastSlotId == focusedId) {
            if (isHotbar(focusedId) && hasData(focusedId)) {
                val data = hotbarBind(focusedId)
                data.binds.clear()
                data.last = 0
                SkyLiteConfig.save()
                Titles.success("Cleared every bind from hotbar slot ${toHotbar(focusedId)}.")
                Titles.playPling()
            } else {
                for (hb in 1..8) {
                    val data = opts.binds["hotbar$hb"] ?: continue
                    if (data.binds.remove(focusedId)) {
                        SkyLiteConfig.save()
                        Titles.success("Successfully unbound slot from hotbar slot $hb.")
                        Titles.playPling()
                    }
                }
            }
            return
        }
        if (lastSlotId < 0 || !canBind(lastSlotId, focusedId)) {
            Titles.error("Invalid slot binding combination.")
            Titles.playBass()
            return
        }
        val hotbar = if (isHotbar(lastSlotId)) lastSlotId else focusedId
        val inv = if (isHotbar(lastSlotId)) focusedId else lastSlotId
        for (hb in 1..8) {
            val data = opts.binds["hotbar$hb"] ?: continue
            if (data.binds.remove(inv)) {
                Titles.warn("Target already bound to hotbar slot $hb, replacing.")
                break
            }
        }
        val name = "hotbar${toHotbar(hotbar)}"
        val data = opts.binds.getOrPut(name) { SkyLiteConfig.HotbarBind() }
        if (inv !in data.binds) data.binds.add(inv)
        SkyLiteConfig.save()
        Titles.success("Slots bound successfully!")
        Titles.playPling()
    }

    private fun drawBorder(context: GuiGraphicsExtractor, menu: AbstractContainerMenu, slotId: Int, color: Int) {
        if (slotId < 0 || slotId >= menu.slots.size) return
        val slot = menu.getSlot(slotId)
        val x = slot.x
        val y = slot.y
        context.fill(x, y, x + 16, y + 1, color)
        context.fill(x, y + 15, x + 16, y + 16, color)
        context.fill(x, y, x + 1, y + 16, color)
        context.fill(x + 15, y, x + 16, y + 16, color)
    }

    private fun hotbarBind(slotId: Int): SkyLiteConfig.HotbarBind =
        opts.binds.getOrPut("hotbar${toHotbar(slotId)}") { SkyLiteConfig.HotbarBind() }

    private fun hasData(slotId: Int): Boolean {
        val data = opts.binds["hotbar${toHotbar(slotId)}"] ?: return false
        return data.binds.isNotEmpty() || data.last != 0
    }

    private fun isHotbar(id: Int): Boolean = id in 36..43

    private fun isInventory(id: Int): Boolean = id in 9..35

    private fun isArmor(id: Int): Boolean = id in 5..8

    private fun isValid(id: Int): Boolean = isHotbar(id) || isInventory(id) || isArmor(id)

    private fun canBind(a: Int, b: Int): Boolean =
        isValid(a) && isValid(b) && (isHotbar(a) || isHotbar(b))

    private fun toHotbar(id: Int): Int = if (id > 9) id % 9 + 1 else id
}
