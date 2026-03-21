package dev.sbs.discordapi.util;

import discord4j.common.util.Snowflake;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Enumeration of Discord deep-link protocol paths using the {@code discord://} URI scheme.
 *
 * <p>
 * Each constant resolves to an absolute {@code discord://-/...} path that can open a
 * specific page or settings panel in the Discord client. Constants are hierarchically
 * composed - child paths are built by appending to their parent's path.
 *
 * @see <a href="https://gist.github.com/ghostrider-05/8f1a0bfc27c7c4509b4ea4e8ce718af0">Discord Deep Links Reference</a>
 * @see Guild
 */
@Getter
@RequiredArgsConstructor
public enum DiscordProtocol {

    /** The root protocol prefix for all Discord deep links. */
    ROOT("discord://-/"),

    // --- Home ---

    /** Friends list page. */
    FRIENDS(ROOT, "channels/@me"),
    /** Discord store page. */
    STORE(ROOT, "store"),
    /** Message requests page. */
    MESSAGE_REQUESTS(ROOT, "message-requests"),
    /** Family centre tab. */
    FAMILY_CENTRE_TAB(ROOT, "family-center"),

    // --- General ---

    /** Applications page. */
    APPS(ROOT, "apps"),
    /** Guild discovery page. */
    DISCOVERY(ROOT, "guild-discovery"),
    /** Create new server page. */
    NEW_SERVER(ROOT, "guilds/create"),

    // --- User Settings ---

    /** User settings root path. */
    USER_SETTINGS(ROOT, "settings/"),
    /** My Account settings page. */
    MY_ACCOUNT(USER_SETTINGS, "account"),
    /** Profile customization settings page. */
    PROFILES(USER_SETTINGS, "profile-customization"),
    /** Privacy and safety settings page. */
    PRIVACY_AND_SAFETY(USER_SETTINGS, "privacy-and-safety"),
    /** Family center settings page. */
    FAMILY_CENTER(USER_SETTINGS, "family-center"),
    /** Authorized apps settings page. */
    AUTHORIZED_APPS(USER_SETTINGS, "authorized-apps"),
    /** Connections settings page. */
    CONNECTIONS(USER_SETTINGS, "connections"),
    /** Clips settings page. */
    CLIPS(USER_SETTINGS, "clips"),
    /** Friend requests settings page. */
    FRIEND_REQUESTS(USER_SETTINGS, "friend-requests"),

    // --- Billing Settings ---

    /** Nitro subscription settings page. */
    NITRO(USER_SETTINGS, "premium"),
    /** Server boost settings page. */
    SERVER_BOOST(USER_SETTINGS, "guild_boosting"),
    /** Subscriptions settings page. */
    SUBSCRIPTIONS(USER_SETTINGS, "subscriptions"),
    /** Gift inventory settings page. */
    GIFT_INVENTORY(USER_SETTINGS, "inventory"),
    /** Billing settings page. */
    BILLING(USER_SETTINGS, "billing"),

    // --- Application Settings ---

    /** Appearance settings page. */
    APPEARANCE(USER_SETTINGS, "appearance"),
    /** Accessibility settings page. */
    ACCESSIBILITY(USER_SETTINGS, "accessibility"),
    /** Voice and video settings page. */
    VOICE_AND_VIDEO(USER_SETTINGS, "voice"),
    /** Text and images settings page. */
    TEXT_AND_IMAGES(USER_SETTINGS, "text"),
    /** Notifications settings page. */
    NOTIFICATIONS(USER_SETTINGS, "notifications"),
    /** Keybinds settings page. */
    KEYBINDS(USER_SETTINGS, "keybinds"),
    /** Language settings page. */
    LANGUAGE(USER_SETTINGS, "locale"),
    /** Windows-specific settings page. */
    WINDOWS_SETTINGS(USER_SETTINGS, "windows"),
    /** Linux-specific settings page. */
    LINUX_SETTINGS(USER_SETTINGS, "linux"),
    /** Streamer mode settings page. */
    STREAMER_MODE(USER_SETTINGS, "streamer-mode"),
    /** Advanced settings page. */
    ADVANCED(USER_SETTINGS, "advanced"),

    // --- Activity ---

    /** Activity privacy settings page. */
    ACTIVITY_PRIVACY(USER_SETTINGS, "activity-privacy"),
    /** Registered games settings page. */
    REGISTERED_GAMES(USER_SETTINGS, "registered-games"),
    /** Game overlay settings page. */
    GAME_OVERLAY(USER_SETTINGS, "overlay"),

    // --- Other Settings ---

    /** What's New / changelogs settings page. */
    WHATS_NEW(USER_SETTINGS, "changelogs"),
    /** Experiments settings page. */
    EXPERIMENTS(USER_SETTINGS, "experiments"),
    /** Developer options settings page. */
    DEVELOPER_OPTIONS(USER_SETTINGS, "developer-options"),
    /** Hotspot options settings page. */
    HOTSPOT_OPTIONS(USER_SETTINGS, "hotspot-options"),
    /** Dismissible content options settings page. */
    DISMISSIBLE_CONTENT_OPTIONS(USER_SETTINGS, "dismissible-content-options"),

    // --- Library Settings ---

    /** Library root path. */
    LIBRARY(ROOT, "library/"),
    /** Library inventory page. */
    LIBRARY_INVENTORY(LIBRARY, "inventory"),
    /** Library settings page. */
    LIBRARY_SETTINGS(LIBRARY, "settings"),

    // --- Account ---

    /** Login page. */
    LOGIN(ROOT, "login"),
    /** Registration page. */
    REGISTER(ROOT, "register"),
    /** Password reset page. */
    RESET(ROOT, "reset"),
    /** Account restore page. */
    RESTORE(ROOT, "restore");

    /**
     * The fully resolved deep-link path for this constant.
     */
    private final @NotNull String path;

    /**
     * Constructs a protocol constant by appending the given path to a parent protocol's path.
     *
     * @param protocol the parent protocol whose path is prepended
     * @param path the path segment to append
     */
    DiscordProtocol(@NotNull DiscordProtocol protocol, @NotNull String path) {
        this(protocol.getPath() + path);
    }

    /**
     * Returns the deep-link path for a gift without a login redirect.
     *
     * @param giftId the gift snowflake identifier
     * @return the fully resolved gift path
     */
    public @NotNull String getGiftPath(@NotNull Snowflake giftId) {
        return this.getGiftPath(giftId, false);
    }

    /**
     * Returns the deep-link path for a gift, optionally including a login redirect.
     *
     * @param giftId the gift snowflake identifier
     * @param login {@code true} to append the login path segment
     * @return the fully resolved gift path
     */
    public @NotNull String getGiftPath(@NotNull Snowflake giftId, boolean login) {
        return ROOT.getPath() + String.format("gifts/%s%s", giftId.asLong(), (login ? "/login" : ""));
    }

    /**
     * Returns the deep-link path to launch a library item.
     *
     * @param sku the SKU snowflake identifier
     * @return the fully resolved launch path
     */
    public @NotNull String getLibraryItemActionPath(@NotNull Snowflake sku) {
        return LIBRARY.getPath() + String.format("%s/launch", sku.asLong());
    }

    /**
     * Returns the deep-link path to a SKU's store page.
     *
     * @param sku the SKU snowflake identifier
     * @return the fully resolved store path
     */
    public @NotNull String getLibraryStorePath(@NotNull Snowflake sku) {
        return ROOT.getPath() + String.format("store/skus/%s", sku.asLong());
    }

    /**
     * Returns the deep-link path to an application's store page.
     *
     * @param appId the application snowflake identifier
     * @return the fully resolved application store path
     */
    public @NotNull String getLibraryAppStorePath(@NotNull Snowflake appId) {
        return ROOT.getPath() + String.format("store/applications/%s", appId.asLong());
    }

    /**
     * Returns the deep-link path for a server invite without a login redirect.
     *
     * @param inviteId the invite code
     * @return the fully resolved invite path
     */
    public @NotNull String getServerInvitePath(@NotNull String inviteId) {
        return this.getServerInvitePath(inviteId, false);
    }

    /**
     * Returns the deep-link path for a server invite, optionally including a login redirect.
     *
     * @param inviteId the invite code
     * @param login {@code true} to append the login path segment
     * @return the fully resolved invite path
     */
    public @NotNull String getServerInvitePath(@NotNull String inviteId, boolean login) {
        return ROOT.getPath() + String.format("invite/%s%s", inviteId, (login ? "/login" : ""));
    }

    /**
     * Guild-scoped deep-link protocol paths, parameterized by guild {@link Snowflake} ID.
     *
     * <p>
     * Paths contain a {@code %s} placeholder that is resolved by calling
     * {@link #getPath(Snowflake)} or one of the specific navigation methods.
     */
    @RequiredArgsConstructor
    public enum Guild {

        /** Guild root path (parameterized by guild ID). */
        ROOT(DiscordProtocol.ROOT, "guilds/%s/"),

        /** Guild settings root path. */
        GUILD_SETTINGS(ROOT, "settings/"),

        // --- General ---

        /** Server overview settings page. */
        OVERVIEW(GUILD_SETTINGS, "overview"),
        /** Roles settings page. */
        ROLES(GUILD_SETTINGS, "roles"),
        /** Emoji settings page. */
        EMOJI(GUILD_SETTINGS, "emoji"),
        /** Stickers settings page. */
        STICKERS(GUILD_SETTINGS, "stickers"),
        /** Soundboard settings page. */
        SOUNDBOARD(GUILD_SETTINGS, "soundboard"),
        /** Widget settings page. */
        WIDGET(GUILD_SETTINGS, "widget"),
        /** Server template settings page. */
        SERVER_TEMPLATE(GUILD_SETTINGS, "guild-templates"),
        /** Custom invite link (vanity URL) settings page. */
        CUSTOM_INVITE_LINK(GUILD_SETTINGS, "vanity-url"),

        // --- Apps ---

        /** Integrations settings page. */
        INTEGRATIONS(GUILD_SETTINGS, "integrations"),
        /** App directory settings page. */
        APP_DIRECTORY(GUILD_SETTINGS, "app-directory"),

        // --- Moderation ---

        /** Safety setup settings page. */
        SAFETY_SETUP(GUILD_SETTINGS, "safety"),
        /** Audit log settings page. */
        AUDIT_LOG(GUILD_SETTINGS, "audit-log"),
        /** Bans settings page. */
        BANS(GUILD_SETTINGS, "bans"),

        // --- Community ---

        /** Community overview settings page. */
        COMMUNITY_OVERVIEW(GUILD_SETTINGS, "community"),
        /** Onboarding settings page. */
        ONBOARDING(GUILD_SETTINGS, "onboarding"),
        /** Server insights / analytics settings page. */
        SERVER_INSIGHTS(GUILD_SETTINGS, "analytics"),
        /** Discovery settings page. */
        DISCOVERY(GUILD_SETTINGS, "discovery"),
        /** Server web page / discovery landing page. */
        SERVER_WEB_PAGE(GUILD_SETTINGS, "discovery-landing-page"),
        /** Welcome page settings. */
        WELCOME_PAGE(GUILD_SETTINGS, "community-welcome"),

        // --- Monetization ---

        /** Server role subscriptions settings page. */
        SERVER_SUBSCRIPTIONS(GUILD_SETTINGS, "role-subscriptions"),
        /** Premium / guild premium settings page. */
        PREMIUM(GUILD_SETTINGS, "guild-premium"),

        // --- User Management ---

        /** Member safety settings page. */
        MEMBERS(GUILD_SETTINGS, "member-safety"),
        /** Instant invites settings page. */
        INVITES(GUILD_SETTINGS, "instant-invites"),

        // --- Other ---

        /** Delete server settings page. */
        DELETE_SERVER(GUILD_SETTINGS, "delete"),

        /** Guild channels root path (parameterized by guild ID). */
        GUILD_CHANNELS(DiscordProtocol.ROOT, "channels/%s/"),

        // --- Channels ---

        /** Channel browser page. */
        BROWSE_CHANNELS(GUILD_CHANNELS, "channel-browser"),
        /** Customize community channels page. */
        CUSTOMIZE_CHANNELS(GUILD_CHANNELS, "customize-community"),
        /** Server guide / home page. */
        SERVER_GUIDE(GUILD_CHANNELS, "@home"),

        // --- Other ---

        /** Member safety page (channel view). */
        MEMBER_SAFETY(GUILD_CHANNELS, "member-safety"),
        /** Role subscriptions page (channel view). */
        ROLE_SUBSCRIPTIONS(GUILD_CHANNELS, "role-subscriptions"),
        /** Membership screening page (parameterized by guild ID). */
        MEMBERSHIP_SCREENING(DiscordProtocol.ROOT, "member-verification/%s");

        /**
         * The path template for this guild-scoped constant.
         */
        private final @NotNull String path;

        /**
         * Constructs a guild constant by appending the given path to a
         * {@link DiscordProtocol} parent's path.
         *
         * @param protocol the parent protocol whose path is prepended
         * @param path the path segment to append
         */
        Guild(@NotNull DiscordProtocol protocol, @NotNull String path) {
            this(protocol.getPath() + path);
        }

        /**
         * Constructs a guild constant by appending the given path to another
         * {@link Guild} parent's path.
         *
         * @param protocol the parent guild constant whose path is prepended
         * @param path the path segment to append
         */
        Guild(@NotNull Guild protocol, @NotNull String path) {
            this(protocol.path + path);
        }

        /**
         * Returns the deep-link path to a specific channel within a guild.
         *
         * @param guildId the guild snowflake identifier
         * @param channelId the channel snowflake identifier
         * @return the fully resolved channel path
         */
        public @NotNull String getChannelPath(@NotNull Snowflake guildId, @NotNull Snowflake channelId) {
            return String.format(GUILD_CHANNELS.path, guildId.asLong()) + channelId.asLong();
        }

        /**
         * Returns the deep-link path to a scheduled event within a guild.
         *
         * @param guildId the guild snowflake identifier
         * @param eventId the event snowflake identifier
         * @return the fully resolved event path
         */
        public @NotNull String getEventPath(@NotNull Snowflake guildId, @NotNull Snowflake eventId) {
            return DiscordProtocol.ROOT.getPath() + String.format("events/%s/%s", guildId.asLong(), eventId.asLong());
        }

        /**
         * Returns the deep-link path to a specific message within a guild channel.
         *
         * @param guildId the guild snowflake identifier
         * @param channelId the channel snowflake identifier
         * @param messageId the message snowflake identifier
         * @return the fully resolved message path
         */
        public @NotNull String getMessagePath(@NotNull Snowflake guildId, @NotNull Snowflake channelId, @NotNull Snowflake messageId) {
            return String.format(GUILD_CHANNELS.path, guildId.asLong()) + String.format("%s/%s", channelId.asLong(), messageId.asLong());
        }

        /**
         * Returns this constant's path with the guild ID placeholder resolved.
         *
         * @param guildId the guild snowflake identifier
         * @return the fully resolved path
         */
        public @NotNull String getPath(@NotNull Snowflake guildId) {
            return String.format(this.path, guildId.asLong());
        }


    }

}
