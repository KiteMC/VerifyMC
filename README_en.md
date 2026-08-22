# VerifyMC

[简体中文](README.md) | English

VerifyMC is a whitelist verification and user management plugin for Minecraft Bukkit, Spigot, Paper, and Folia servers. Chinese is the default language, with English available through the language switcher. The plugin provides web registration, email verification, review management, bans, AuthMe integration, registration questionnaires, Discord linking, and BungeeCord/Waterfall/Velocity proxy support.

## Features

- Web-based registration and whitelist application review
- Automatic approval and manual administrator review
- Email verification and email-domain whitelisting
- Built-in math or text CAPTCHA without external services
- User bans, unbans, password changes, and profile management
- AuthMe password and user synchronization
- Optional registration questionnaires with LLM scoring
- Discord OAuth2 account linking
- Bukkit native whitelist, plugin-managed whitelist, and MySQL storage modes
- BungeeCord, Waterfall, and Velocity proxy support
- GlassX administration frontend with real-time WebSocket review notifications
- Automatic resource updates and backups for configuration and language files

## Requirements

- Java 17 or newer
- Bukkit, Spigot, Paper, or Folia 1.20+
- A working SMTP mailbox when email verification is enabled
- An HTTPS domain is recommended for production deployments

## Installation and Configuration

1. Download the latest `verifymc-version.jar` from [GitHub Releases](https://github.com/KiteMC/VerifyMC/releases).
2. Put the main plugin in the server's `plugins` directory. If you use a proxy, also install the matching `verifymc-proxy-version.jar`.
3. Start the server to generate the default configuration files.
4. Edit `plugins/VerifyMC/config.yml` and configure the web port, registration URL, authentication methods, and storage mode.
5. Restart the server and open `http://your-server-address:8080`.

### Administrator Login

The admin panel authenticates against the server OP list; there is no separate default admin password:

1. Register the administrator account through the web page and keep its username (or email) and password.
2. Run `/op player_name` from the console or in-game to add that Minecraft user to the server OP list.
3. Open “Login” in the web page header and sign in. The dashboard will show the administrator menus after a successful login.

If the login reports that the account is not authorized, confirm that the username matches the OP entry in `ops.json`, then sign in again.

Chinese is used by default. Users can switch to English with the web language switcher or configure the plugin language explicitly:

```yaml
language: zh # Use zh or en
```

### Quick Start

To start without SMTP, use the built-in CAPTCHA:

```yaml
auth_methods:
  - captcha
whitelist_mode: plugin
web_register_url: https://your-domain.example/
```

For a small private server, automatic approval can be enabled:

```yaml
register:
  auto_approve: true
```

## Building

### Frontend

```bash
cd frontend/glassx
npm ci
npm run build
```

### Main Plugin

```bash
cd plugin
mvn clean package
```

Output: `plugin/target/verifymc-version.jar`

### Proxy Plugin

```bash
cd plugin-proxy
mvn clean package
```

Output: `plugin-proxy/target/verifymc-proxy-version.jar`

## Tests and Quality Checks

```bash
cd frontend/glassx
npm run lint
npm run type-check
npm run test
```

```bash
cd plugin
mvn test

cd ../plugin-proxy
mvn test
```

## Release

The build workflow is `.github/workflows/plugin.yml`; it builds and uploads artifacts only.

To publish a release, manually run `.github/workflows/release.yml` from GitHub Actions. Release notes are read from `version.yml`, with the Chinese section first and the English section second. No separate language-specific release notes file is maintained.

## Documentation and Community

- [Official Chinese Documentation](https://kitemc.com/zh/docs/verifymc/)
- [Official English Documentation](https://kitemc.com/docs/VerifyMC/)
- [GitHub Releases](https://github.com/KiteMC/VerifyMC/releases)
- QQ Group: 1041540576
- Discord: [https://discord.gg/TCn9v88V](https://discord.gg/TCn9v88V)

## Feedback

Issues, suggestions, and pull requests are welcome. Star the project on GitHub if it is useful to you.
