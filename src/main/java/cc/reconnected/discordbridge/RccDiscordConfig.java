package cc.reconnected.discordbridge;

import cc.reconnected.library.config.Config;

import java.util.List;
import java.util.Map;

@Config(RccDiscord.MOD_ID)
public class RccDiscordConfig {
    public String token = "";
    public String name = "rcc-bridge";
    public String channelId = "00000";
    public String roleId = "00000";
    public String avatarApiUrl = "https://mc-heads.net/head/{{uuid}}";
    public String avatarApiThumbnailUrl = "https://mc-heads.net/head/{{uuid}}/32";
    public String inviteLink = "https://discord.gg/myinvite";
    public String serverAvatarUrl = "";

    // https://docs.advntr.dev/minimessage/format.html

    public String prefix = "<#5865F2><hover:show_text:'This is a message from the Discord server'><click:open_url:'https://discord.gg/myserver'>D<reset>";
    public String reply = " <reference_username> <hover:show_text:'Message: <reference_message>'><gray>↵</gray>";
    public String forward = " <hover:show_text:'Message: <reference_message>'><gray>Forwarded ↪</gray>";

    public String messageFormat = "<prefix> <username><gray>:</gray><reply><forward> <message>";

    public boolean usePresence = true;
    public boolean enableSlashCommands = true;
    public boolean enableItemPreviews = true;
    public String itemPreviewToken = "[item]";
    public String itemPreviewThumbnailUrl = "https://api.reconnected.cc/assets/{mod}/{item}.png";
    public int itemPreviewMaxTooltipLines = 6;
    public String linkedPermissionNode = "rcc.chatbox.linked";

    public Map<String, String> autoReplacementsM2D = Map.ofEntries(
            Map.entry(":mod_badge:", "<:mod_badge:1397905859857874975>"),
            Map.entry(":adm_badge:", "<:adm_badge:1397906959478292490>"),
            Map.entry(":supt1_badge:", "<:supt1_badge:1397908368105930792>"),
            Map.entry(":supt2_badge:", "<:supt2_badge:1397910009639075860>"),
            Map.entry(":supt3_badge:", "<:supt3_badge:1397910008280252426>"),
            Map.entry(":beta_badge:", "<:beta_badge:1397910006321643742>"),
            Map.entry(":dscd_badge:", "<:dscd_badge:1397910005243711589>"),
            Map.entry("\ue002", "<:dev_badge:1397904759557455924>"),
            Map.entry("\ue003", "<:mod_badge:1397905859857874975>"),
            Map.entry("\ue004", "<:adm_badge:1397906959478292490>"),
            Map.entry("\ue005", "<:supt1_badge:1397908368105930792>"),
            Map.entry("\ue006", "<:supt2_badge:1397910009639075860>"),
            Map.entry("\ue007", "<:supt3_badge:1397910008280252426>"),
            Map.entry("\ue008", "<:beta_badge:1397910006321643742>"),
            Map.entry("\ue009", "<:dscd_badge:1397910005243711589>")
    );
    public Map<String, String> autoReplacementsD2M = Map.ofEntries(
            Map.entry("<:dev_badge:1397904759557455924>", "\ue002"),
            Map.entry("<:mod_badge:1397905859857874975>", "\ue003"),
            Map.entry("<:adm_badge:1397906959478292490>", "\ue004"),
            Map.entry("<:supt1_badge:1397908368105930792>", "\ue005"),
            Map.entry("<:supt2_badge:1397910009639075860>", "\ue006"),
            Map.entry("<:supt3_badge:1397910008280252426>", "\ue007"),
            Map.entry("<:beta_badge:1397910006321643742>", "\ue008"),
            Map.entry("<:dscd_badge:1397910005243711589>", "\ue009")
    );

    public List<String> allowedRoleMentions = List.of();
}
