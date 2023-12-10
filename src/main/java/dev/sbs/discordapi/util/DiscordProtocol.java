package dev.sbs.discordapi.util;

import discord4j.common.util.Snowflake;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public enum DiscordProtocol {

    // https://gist.github.com/ghostrider-05/8f1a0bfc27c7c4509b4ea4e8ce718af0
    ROOT("discord://-/"),

    // Home
    FRIENDS(ROOT, "channels/@me"),
    STORE(ROOT, "store"),
    MESSAGE_REQUESTS(ROOT, "message-requests"),
    FAMILY_CENTRE_TAB(ROOT, "family-center"),

    // General
    APPS(ROOT, "apps"),
    DISCOVERY(ROOT, "guild-discovery"),
    NEW_SERVER(ROOT, "guilds/create"),

    // User Settings
    USER_SETTINGS(ROOT, "settings/"),
    MY_ACCOUNT(USER_SETTINGS, "account"),
    PROFILES(USER_SETTINGS, "profile-customization"),
    PRIVACY_AND_SAFETY(USER_SETTINGS, "privacy-and-safety"),
    FAMILY_CENTER(USER_SETTINGS, "family-center"),
    AUTHORIZED_APPS(USER_SETTINGS, "authorized-apps"),
    CONNECTIONS(USER_SETTINGS, "connections"),
    CLIPS(USER_SETTINGS, "clips"),
    FRIEND_REQUESTS(USER_SETTINGS, "friend-requests"),
    // Billing Settings
    NITRO(USER_SETTINGS, "premium"),
    SERVER_BOOST(USER_SETTINGS, "guild_boosting"),
    SUBSCRIPTIONS(USER_SETTINGS, "subscriptions"),
    GIFT_INVENTORY(USER_SETTINGS, "inventory"),
    BILLING(USER_SETTINGS, "billing"),
    // Application Settings
    APPEARANCE(USER_SETTINGS, "appearance"),
    ACCESSIBILITY(USER_SETTINGS, "accessibility"),
    VOICE_AND_VIDEO(USER_SETTINGS, "voice"),
    TEXT_AND_IMAGES(USER_SETTINGS, "text"),
    NOTIFICATIONS(USER_SETTINGS, "notifications"),
    KEYBINDS(USER_SETTINGS, "keybinds"),
    LANGUAGE(USER_SETTINGS, "locale"),
    WINDOWS_SETTINGS(USER_SETTINGS, "windows"),
    LINUX_SETTINGS(USER_SETTINGS, "linux"),
    STREAMER_MODE(USER_SETTINGS, "streamer-mode"),
    ADVANCED(USER_SETTINGS, "advanced"),
    // Activity
    ACTIVITY_PRIVACY(USER_SETTINGS, "activity-privacy"),
    REGISTERED_GAMES(USER_SETTINGS, "registered-games"),
    GAME_OVERLAY(USER_SETTINGS, "overlay"),
    // Other
    WHATS_NEW(USER_SETTINGS, "changelogs"),
    EXPERIMENTS(USER_SETTINGS, "experiments"),
    DEVELOPER_OPTIONS(USER_SETTINGS, "developer-options"),
    HOTSPOT_OPTIONS(USER_SETTINGS, "hotspot-options"),
    DISMISSIBLE_CONTENT_OPTIONS(USER_SETTINGS, "dismissible-content-options"),

    // Library Settings
    LIBRARY(ROOT, "library/"),
    LIBRARY_INVENTORY(LIBRARY, "inventory"),
    LIBRARY_SETTINGS(LIBRARY, "settings"),

    // Account
    LOGIN(ROOT, "login"),
    REGISTER(ROOT, "register"),
    RESET(ROOT, "reset"),
    RESTORE(ROOT, "restore");

    private final @NotNull String path;

    DiscordProtocol(@NotNull DiscordProtocol protocol, @NotNull String path) {
        this(protocol.getPath() + path);
    }

    public @NotNull String getGiftPath(@NotNull Snowflake giftId) {
        return this.getGiftPath(giftId, false);
    }

    public @NotNull String getGiftPath(@NotNull Snowflake giftId, boolean login) {
        return ROOT.getPath() + String.format("gifts/%s%s", giftId.asLong(), (login ? "/login" : ""));
    }

    public @NotNull String getLibraryItemActionPath(@NotNull Snowflake sku) {
        return LIBRARY.getPath() + String.format("%s/launch", sku.asLong());
    }

    public @NotNull String getLibraryStorePath(@NotNull Snowflake sku) {
        return ROOT.getPath() + String.format("store/skus/%s", sku.asLong());
    }
    public @NotNull String getLibraryAppStorePath(@NotNull Snowflake appId) {
        return ROOT.getPath() + String.format("store/applications/%s", appId.asLong());
    }

    public @NotNull String getServerInvitePath(@NotNull String inviteId) {
        return this.getServerInvitePath(inviteId, false);
    }

    public @NotNull String getServerInvitePath(@NotNull String inviteId, boolean login) {
        return ROOT.getPath() + String.format("invite/%s%s", inviteId, (login ? "/login" : ""));
    }

    @RequiredArgsConstructor
    public enum Guild {

        ROOT(DiscordProtocol.ROOT, "guilds/%s/"),

        GUILD_SETTINGS(ROOT, "settings/"),
        // General
        OVERVIEW(GUILD_SETTINGS, "overview"),
        ROLES(GUILD_SETTINGS, "roles"),
        EMOJI(GUILD_SETTINGS, "emoji"),
        STICKERS(GUILD_SETTINGS, "stickers"),
        SOUNDBOARD(GUILD_SETTINGS, "soundboard"),
        WIDGET(GUILD_SETTINGS, "widget"),
        SERVER_TEMPLATE(GUILD_SETTINGS, "guild-templates"),
        CUSTOM_INVITE_LINK(GUILD_SETTINGS, "vanity-url"),
        // Apps
        INTEGRATIONS(GUILD_SETTINGS, "integrations"),
        APP_DIRECTORY(GUILD_SETTINGS, "app-directory"),
        // Moderation
        SAFETY_SETUP(GUILD_SETTINGS, "safety"),
        AUDIT_LOG(GUILD_SETTINGS, "audit-log"),
        BANS(GUILD_SETTINGS, "bans"),
        // Community
        COMMUNITY_OVERVIEW(GUILD_SETTINGS, "community"),
        ONBOARDING(GUILD_SETTINGS, "onboarding"),
        SERVER_INSIGHTS(GUILD_SETTINGS, "analytics"),
        DISCOVERY(GUILD_SETTINGS, "discovery"),
        SERVER_WEB_PAGE(GUILD_SETTINGS, "discovery-landing-page"),
        WELCOME_PAGE(GUILD_SETTINGS, "community-welcome"),
        // Monetization
        SERVER_SUBSCRIPTIONS(GUILD_SETTINGS, "role-subscriptions"),
        PREMIUM(GUILD_SETTINGS, "guild-premium"),
        // User Management
        MEMBERS(GUILD_SETTINGS, "member-safety"),
        INVITES(GUILD_SETTINGS, "instant-invites"),
        // Other
        DELETE_SERVER(GUILD_SETTINGS, "delete"),

        GUILD_CHANNELS(DiscordProtocol.ROOT, "channels/%s/"),
        // Channels
        BROWSE_CHANNELS(GUILD_CHANNELS, "channel-browser"),
        CUSTOMIZE_CHANNELS(GUILD_CHANNELS, "customize-community"),
        SERVER_GUIDE(GUILD_CHANNELS, "@home"),
        // Other
        MEMBER_SAFETY(GUILD_CHANNELS, "member-safety"),
        ROLE_SUBSCRIPTIONS(GUILD_CHANNELS, "role-subscriptions"),
        MEMBERSHIP_SCREENING(DiscordProtocol.ROOT, "member-verification/%s");

        private final @NotNull String path;

        Guild(@NotNull DiscordProtocol protocol, @NotNull String path) {
            this(protocol.getPath() + path);
        }

        Guild(@NotNull Guild protocol, @NotNull String path) {
            this(protocol.path + path);
        }

        public @NotNull String getChannelPath(@NotNull Snowflake guildId, @NotNull Snowflake channelId) {
            return String.format(GUILD_CHANNELS.path, guildId.asLong()) + channelId.asLong();
        }

        public @NotNull String getEventPath(@NotNull Snowflake guildId, @NotNull Snowflake eventId) {
            return DiscordProtocol.ROOT.getPath() + String.format("events/%s/%s", guildId.asLong(), eventId.asLong());
        }

        public @NotNull String getMessagePath(@NotNull Snowflake guildId, @NotNull Snowflake channelId, @NotNull Snowflake messageId) {
            return String.format(GUILD_CHANNELS.path, guildId.asLong()) + String.format("%s/%s", channelId.asLong(), messageId.asLong());
        }

        public @NotNull String getPath(@NotNull Snowflake guildId) {
            return String.format(this.path, guildId.asLong());
        }


    }

}
