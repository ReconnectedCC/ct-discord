package cc.reconnected.discordbridge;

import cc.reconnected.discordbridge.commands.DiscordCommand;
import cc.reconnected.discordbridge.discord.Client;
import cc.reconnected.library.config.ConfigManager;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RccDiscord implements ModInitializer {

    public static final String MOD_ID = "rcc-discord";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RccDiscord INSTANCE;

    public static RccDiscordConfig CONFIG;
    private Client client;
    private MinecraftServer mcServer;

    private static final Queue<Component> chatQueue = new ConcurrentLinkedQueue<>();

    public RccDiscord() {
        INSTANCE = this;
    }

    public static RccDiscord getInstance() {
        return INSTANCE;
    }

    public Client getClient() {
        return client;
    }

    public MinecraftServer getServer() {
        return mcServer;
    }

    public static final Cache<@NotNull String, UUID> linkCodes = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(1024).build();

    /**
     * Discord snowflake ID -> Player UUID
     */
    public static ConcurrentHashMap<String, UUID> discordLinks = new ConcurrentHashMap<>();
    private static Path dataDirectory;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Discord Bridge");

        try {
            CONFIG = ConfigManager.load(RccDiscordConfig.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load config. Refusing to continue.", e);
            return;
        }

        try {
            this.client = new Client();
        } catch (Exception e) {
            LOGGER.error("Error creating Discord client", e);
            return;
        }

        CommandRegistrationCallback.EVENT.register(DiscordCommand::register);

        BridgeEvents.register(this);

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            while (!chatQueue.isEmpty()) {
                var message = chatQueue.poll();

                LOGGER.info(PlainTextComponentSerializer.plainText().serialize(message));

                var list = server.getPlayerManager().getPlayerList();
                for (ServerPlayerEntity player : list) {
                    player.sendMessage(message);
                }
            }
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            dataDirectory = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(MOD_ID);
            mcServer = server;
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // load discord id map
            if (!dataDirectory.toFile().isDirectory()) {
                if (!dataDirectory.toFile().mkdir()) {
                    LOGGER.error("Failed to create rcc-discord data directory");
                }
            }

            var mapPath = dataDirectory.resolve("links.json");
            if (mapPath.toFile().exists()) {
                try (var stream = new BufferedReader(new FileReader(mapPath.toFile(), StandardCharsets.UTF_8))) {
                    var type = new TypeToken<ConcurrentHashMap<String, UUID>>() {
                    }.getType();
                    discordLinks = new Gson().fromJson(stream, type);
                } catch (IOException e) {
                    LOGGER.error("Exception reading licenses data", e);
                }
            } else {
                discordLinks = new ConcurrentHashMap<>();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // nuke the client
            shutdownNow();
        });
    }

    public void shutdown() {
        var httpClient = client.client().getHttpClient();
        client.client().shutdown();
        httpClient.connectionPool().evictAll();
        httpClient.dispatcher().executorService().shutdown();
    }

    public void shutdownNow() {
        var httpClient = client.client().getHttpClient();
        client.client().shutdownNow();
        httpClient.connectionPool().evictAll();
        httpClient.dispatcher().executorService().shutdownNow();
        try {
        client.client().awaitShutdown();
        } catch (InterruptedException e) {
            LOGGER.error("Error shutting down Discord client", e);
        }
    }

    public static void enqueueMessage(Component component) {
        chatQueue.offer(component);
    }

    public void sendServerStatus(String message, int color) {
        if (client.isNotReady())
            return;
        var embed = new WebhookEmbedBuilder()
                .setDescription(message)
                .setColor(color)
                .build();
        client.webhookClient().send(embed);
    }

    public void sendPlayerStatus(String message, int color, String avatarUrl) {
        if (client.isNotReady())
            return;
        var embed = new WebhookEmbedBuilder()
                .setAuthor(new WebhookEmbed.EmbedAuthor(message, avatarUrl, null))
                .setColor(color)
                .build();
        client.webhookClient().send(embed);
    }

    public void sendPlayerMessage(String message, String name, String avatarUrl) {
        sendPlayerMessage(message, null, name, avatarUrl);
    }

    public void sendPlayerMessage(String message, @Nullable ServerPlayerEntity player, String name, String avatarUrl) {
        if (client.isNotReady())
            return;
        for (Map.Entry<String, String> replacement : CONFIG.autoReplacementsM2D.entrySet()) {
            message = message.replaceAll(replacement.getKey(), replacement.getValue());
        }
        var itemPreview = makeItemPreview(message, player);
        var builder = new WebhookMessageBuilder()
                .setAvatarUrl(avatarUrl)
                .setUsername(name)
                .setContent(itemPreview.message())
                .setAllowedMentions(
                        new AllowedMentions()
                                .withParseUsers(true)
                                .withRoles(CONFIG.allowedRoleMentions)
                                .withParseEveryone(false)
                );
        if (itemPreview.embed() != null) {
            builder.addEmbeds(itemPreview.embed());
        }
        client.webhookClient().send(builder.build());
    }

    private ItemPreview makeItemPreview(String message, @Nullable ServerPlayerEntity player) {
        if (!CONFIG.enableItemPreviews || player == null || CONFIG.itemPreviewToken.isBlank() || !message.contains(CONFIG.itemPreviewToken)) {
            return new ItemPreview(message, null);
        }

        var stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            return new ItemPreview(replaceItemToken(message, "Air"), makeEmptyItemEmbed());
        }

        var itemName = stack.getName().getString();
        var outputMessage = replaceItemToken(message, itemName);
        var embed = makeItemEmbed(stack, player, itemName);
        return new ItemPreview(outputMessage, embed);
    }

    private WebhookEmbed makeItemEmbed(ItemStack stack, ServerPlayerEntity player, String itemName) {
        var tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, player, TooltipType.BASIC)
                .stream()
                .map(Text::getString)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.equals(itemName))
                .limit(Math.max(CONFIG.itemPreviewMaxTooltipLines, 0))
                .map(RccDiscord::escapeDiscordText)
                .toList();

        var description = new StringBuilder();
        if (!tooltip.isEmpty()) {
            for (var line : tooltip) {
                description.append(line).append('\n');
            }
        }

        var descriptionText = description.toString().trim();

        var title = escapeDiscordText(itemName);
        if (stack.getCount() > 1) {
            title += " x" + stack.getCount();
        }

        var itemId = Registries.ITEM.getId(stack.getItem());
        var builder = new WebhookEmbedBuilder()
                .setTitle(new WebhookEmbed.EmbedTitle(title, null))
                .setThumbnailUrl(makeItemThumbnailUrl(itemId.getNamespace(), itemId.getPath()))
                .setFooter(new WebhookEmbed.EmbedFooter(itemId.toString(), null))
                .setColor(0x5865F2);
        if (!descriptionText.isEmpty()) {
            builder.setDescription(truncateDiscordText(descriptionText, 1000));
        }
        if (stack.isDamageable()) {
            builder.addField(new WebhookEmbed.EmbedField(
                    true,
                    "Durability",
                    (stack.getMaxDamage() - stack.getDamage()) + " / " + stack.getMaxDamage()
            ));
        }

        return builder.build();
    }

    private WebhookEmbed makeEmptyItemEmbed() {
        return new WebhookEmbedBuilder()
                .setTitle(new WebhookEmbed.EmbedTitle("Air", null))
                .setThumbnailUrl(makeItemThumbnailUrl("minecraft", "air"))
                .setFooter(new WebhookEmbed.EmbedFooter("minecraft:air", null))
                .setColor(0x5865F2)
                .build();
    }

    private static String makeItemThumbnailUrl(String mod, String item) {
        return CONFIG.itemPreviewThumbnailUrl
                .replace("{mod}", mod)
                .replace("{item}", item);
    }

    private static String replaceItemToken(String message, String itemName) {
        return message.replaceFirst(Pattern.quote(CONFIG.itemPreviewToken), Matcher.quoteReplacement("**[" + escapeDiscordText(itemName) + "]**"));
    }

    private static String escapeDiscordText(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace("|", "\\|")
                .replace("@", "@\u200B");
    }

    private static String truncateDiscordText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, Math.max(maxLength - 3, 0)) + "...";
    }

    private record ItemPreview(String message, @Nullable WebhookEmbed embed) {
    }

    public void setStatus(String string) {
        setStatus(OnlineStatus.ONLINE, Activity.playing(string));
    }

    public void setStatus(OnlineStatus status, Activity activity) {
        client.client().getPresence().setPresence(status, activity);
    }

    public void saveData() {
        var output = new Gson().toJson(discordLinks);
        try (var stream = new FileWriter(dataDirectory.resolve("links.json").toFile(), StandardCharsets.UTF_8)) {
            stream.write(output);
        } catch (IOException e) {
            LOGGER.error("Exception Discord links map data", e);
        }
    }

    public String[] getPlayerNames() {
        return mcServer.getPlayerManager().getPlayerNames();
    }

    public Optional<ServerPlayerEntity> getPlayer(UUID uuid) {
        var player = mcServer.getPlayerManager().getPlayer(uuid);
        return Optional.ofNullable(player);
    }
}
