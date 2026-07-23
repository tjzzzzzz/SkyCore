package dev.skycore.config.ui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder
import dev.skycore.config.SkyCoreConfig
import net.minecraft.network.chat.Component

class SkyCoreModMenu : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent ->
            YetAnotherConfigLib.create(SkyCoreConfig.handler()) { defaults, config, builder ->
                builder
                    .title(Component.literal("SkyCore"))
                    .category(
                        ConfigCategory.createBuilder()
                            .name(Component.translatable("skycore.category.general"))
                            .option(
                                Option.createBuilder<Boolean>()
                                    .name(Component.translatable("skycore.option.enabled"))
                                    .binding(defaults.enabled, { config.enabled }, { config.enabled = it })
                                    .controller { BooleanControllerBuilder.create(it).coloured(true) }
                                    .build()
                            )
                            .option(
                                Option.createBuilder<Float>()
                                    .name(Component.translatable("skycore.option.hud_scale"))
                                    .binding(defaults.hudScale, { config.hudScale }, { config.hudScale = it })
                                    .controller {
                                        FloatSliderControllerBuilder.create(it)
                                            .range(0.5f, 2.0f)
                                            .step(0.05f)
                                    }
                                    .build()
                            )
                            .build()
                    )
            }.generateScreen(parent)
        }
}
