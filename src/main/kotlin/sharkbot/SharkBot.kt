package sharkbot

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import shark.SharkBot
import shark.WrappedSharkEvent
import shark.data.plugin.IPlugin
import shark.data.plugin.Plugin
import shark.data.plugin.PluginConfig
import shark.data.registry.ResourceLocation
import shark.data.resource.Locale
import shark.event.EventBus
import shark.event.application.ClientSetupEvent
import shark.event.application.plugin.PluginLoadEvent
import shark.event.application.resource.ResourceLoadEvent
import sharkbot.data.resource.StaticResourcePool
import sharkbot.registry.Commands
import java.util.*

object SharkBotDefaultPlugin : IPlugin {

    override var eventBus: EventBus? = EventBus(this::class)

    override val mainClass = SharkBotDefaultPluginClass::class.java
    override val pluginLoader = SharkBot.pluginLoader

    override var config: PluginConfig? = PluginConfig(
        name = "SharkBot Default Plugin",
        id = pluginAnnotation.id
    )

    override var instance: Any? = null
    override var loaded = false
        set(_) { field = true }
    override var loader: ClassLoader? = SharkBotDefaultPluginClass::class.java.classLoader

    override fun load(event: PluginLoadEvent) {
        instance = SharkBotDefaultPluginClass(event)
    }

    override fun toString(): String {
        return "Plugin [${this.javaClass.name}]"
    }

}

@Plugin("shark")
class SharkBotDefaultPluginClass(val event: PluginLoadEvent) {

    companion object {

        private lateinit var instance: SharkBotDefaultPluginClass

        fun getInstance() = instance

    }

    init {
        instance = this
    }

    val eventBus = event.plugin.eventBus!!

    init {
        SharkBot.resourceLoader.eventBus.on <ResourceLoadEvent> { event ->
            val location = event.wrappedEvent.location
            val resources = event.wrappedEvent.resources
            if (location.path == "static") {
                for (resource in resources) {
                    val resourceLocation = resource.location
                    val processedPath = resourceLocation.path
                        .split("/")
                        .let { it.subList(1, it.size) }
                    if (processedPath.isEmpty()) continue
                    val processedLocation = ResourceLocation.of(
                        resourceLocation.namespace,
                        processedPath.joinToString("/")
                    )
                    StaticResourcePool[processedLocation] = resource
                }
            }
        }
        SharkBot.resourceLoader.eventBus.on <ResourceLoadEvent> { event ->
            val location = event.wrappedEvent.location
            val resources = event.wrappedEvent.resources
            if (location.path == "lang") {
                val mapper = ObjectMapper()
                for (resource in resources) {
                    if (!Objects.requireNonNull(resource.resource.filename)!!.endsWith(".json")) continue
                    val name = resource.resource.filename!!.split(".")[0]
                    try {
                        val value = mapper.readValue(
                            resource.resource.inputStream,
                            object : TypeReference<Map<String, String>>() {}
                        )
                        if (!Locale.instances.containsKey(name)) Locale.instances[name] = Locale()
                        value!!.forEach {
                            Locale.instances[name]!![it.key] = it.value
                        }
                    } catch (ignored: Throwable) {}
                }
            }
        }
        eventBus.on <WrappedSharkEvent> { event ->
            event.wrappedEvent.wrappedSharkEvent.isEvent(this::onClientSetup)
        }
    }

    init {
        Commands.bootstrap()
    }

    fun onClientSetup(event: ClientSetupEvent) {
        event.setCancelled(true)
    }

}