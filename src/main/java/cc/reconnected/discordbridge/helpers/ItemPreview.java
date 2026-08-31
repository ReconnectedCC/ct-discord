package cc.reconnected.discordbridge.helpers;

import cc.reconnected.discordbridge.Utils;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cc.reconnected.discordbridge.RccDiscord.CONFIG;


public record ItemPreview(String message, @Nullable ServerPlayerEntity player, @Nullable WebhookEmbed embed) {
    public ItemPreview {
        if (!CONFIG.enableItemPreviews || player == null || CONFIG.itemPreviewToken.isBlank() || !message.contains(CONFIG.itemPreviewToken)) {
            player = null;
            embed = null;
        } else {
            var stack = player.getMainHandStack();
            if (stack.isEmpty()) {
                message = replaceItemToken(message, "Air");
                embed = makeEmptyItemEmbed();
            } else {
                var itemName = stack.getName().getString();
                message = replaceItemToken(message, itemName);
                embed = makeItemEmbed(stack, player, itemName);
            }
        }
    }

    public ItemPreview(String message, @Nullable ServerPlayerEntity player) {
        this(message, player, null);
    }

    private static String replaceItemToken(String message, String itemName) {
        return message.replaceFirst(Pattern.quote(CONFIG.itemPreviewToken), Matcher.quoteReplacement("**[" + Utils.escapeDiscordText(itemName) + "]**"));
    }
    private static String makeItemThumbnailUrl(String mod, String item) {
        return CONFIG.itemPreviewThumbnailUrl
                .replace("{mod}", mod)
                .replace("{item}", item);
    }
    private WebhookEmbed makeEmptyItemEmbed() {
        return new WebhookEmbedBuilder()
                .setTitle(new WebhookEmbed.EmbedTitle("Air", null))
                .setThumbnailUrl(makeItemThumbnailUrl("minecraft", "air"))
                .setFooter(new WebhookEmbed.EmbedFooter("minecraft:air", null))
                .setColor(0x5865F2)
                .build();
    }

    private WebhookEmbed makeItemEmbed(ItemStack stack, ServerPlayerEntity player, String itemName) {
        var registry = player.getRegistryManager().toImmutable();
        var tooltip = stack.getTooltip(Item.TooltipContext.create(registry), player, TooltipType.BASIC)
                .stream()
                .map(Text::getString)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.equals(itemName))
                .limit(Math.max(CONFIG.itemPreviewMaxTooltipLines, 0))
                .map(Utils::escapeDiscordText)
                .toList();

        var description = new StringBuilder();
        if (!tooltip.isEmpty()) {
            for (var line : tooltip) {
                description.append(line).append('\n');
            }
        }

        var descriptionText = description.toString().trim();

        var title = Utils.escapeDiscordText(itemName);
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
            builder.setDescription(Utils.truncateText(descriptionText, 1000));
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
}


