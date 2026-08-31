package cc.reconnected.discordbridge;

import net.minecraft.server.network.ServerPlayerEntity;

public class Utils {
    public static String getAvatarUrl(ServerPlayerEntity player) {
        var avatarApiUrl = RccDiscord.CONFIG.avatarApiUrl;
        return avatarApiUrl.replaceAll("\\{\\{uuid}}", player.getUuidAsString());
    }

    public static String getAvatarThumbnailUrl(ServerPlayerEntity player) {
        var avatarApiUrl = RccDiscord.CONFIG.avatarApiThumbnailUrl;
        return avatarApiUrl.replaceAll("\\{\\{uuid}}", player.getUuidAsString());
    }

    public static String escapeDiscordText(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace("|", "\\|")
                .replace("@", "@\u200B");
    }

    public static String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, Math.max(maxLength - 3, 0)) + "...";
    }
}
