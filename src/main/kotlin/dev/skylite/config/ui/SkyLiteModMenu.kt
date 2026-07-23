package dev.skylite.config.ui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder
import dev.skylite.config.SkyLiteConfig
import net.minecraft.network.chat.Component

/**
 * hands mod menu the yacl screen. option definitions stay here, defaults and
 * persistence stay in [SkyLiteConfig].
 */
class SkyLiteModMenu : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent ->
            YetAnotherConfigLib.create(SkyLiteConfig.handler()) { defaults, config, builder ->
                builder
                    .title(Component.literal("SkyLite"))
                    .category(
                        ConfigCategory.createBuilder()
                            .name(Component.translatable("skylite.category.general"))
                            .option(
                                Option.createBuilder<Boolean>()
                                    .name(Component.translatable("skylite.option.enabled"))
                                    .binding(defaults.enabled, { config.enabled }, { config.enabled = it })
                                    .controller { BooleanControllerBuilder.create(it).coloured(true) }
                                    .build()
                            )
                            .option(
                                Option.createBuilder<Float>()
                                    .name(Component.translatable("skylite.option.hud_scale"))
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
