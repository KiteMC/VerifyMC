package team.kitemc.verifymc.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.db.AuditRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the /vmc command. Refactored from the inline onCommand method
 * in the original 878-line VerifyMC class.
 * <p>
 * Subcommands:
 *   reload    — Reload configuration
 *   approve   — Approve a pending user
 *   reject    — Reject a pending user
 *   delete    — Delete a user
 *   ban       — Ban a user
 *   unban     — Unban a user
 *   list      — List users by status
 *   info      — Show user info
 *   version   — Show plugin version
 */
public class VmcCommandExecutor implements CommandExecutor, TabCompleter {
    private final PluginContext ctx;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "reload", "approve", "reject", "delete", "ban", "unban", "list", "info", "version"
    );

    public VmcCommandExecutor(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6[VerifyMC] §fUsage: /vmc <" + String.join("|", SUBCOMMANDS) + ">");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "approve" -> handleApprove(sender, args);
            case "reject" -> handleReject(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "ban" -> handleBan(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "version" -> handleVersion(sender);
            default -> sender.sendMessage("§6[VerifyMC] §cUnknown subcommand: " + sub);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("approve", "reject", "delete", "ban", "unban", "info").contains(sub)) {
                // Return registered usernames that match partial input
                return ctx.getUserDao().getAllUsers().stream()
                        .map(u -> (String) u.get("username"))
                        .filter(name -> name != null && name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .limit(20)
                        .collect(Collectors.toList());
            }
            if ("list".equals(sub)) {
                return Arrays.asList("all", "pending", "approved", "rejected", "banned");
            }
        }
        return List.of();
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("verifymc.admin")) {
            sender.sendMessage("§6[VerifyMC] §cNo permission.");
            return;
        }
        ctx.getConfigManager().reloadConfig();
        ctx.getI18nManager().clearCache();
        ctx.getI18nManager().init(ctx.getConfigManager().getLanguage());
        ctx.reloadRuntimeConfigDependents();
        sender.sendMessage("§6[VerifyMC] §aConfiguration reloaded.");
    }

    private void handleApprove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("verifymc.admin")) {
            sender.sendMessage("§6[VerifyMC] §cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§6[VerifyMC] §fUsage: /vmc approve <username>");
            return;
        }
        String target = args[1];
        String storedTarget = ctx.resolveStoredUsername(target);
        boolean ok = storedTarget != null && ctx.getUserDao().updateUserStatus(storedTarget, "approved", sender.getName());
        if (ok) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + storedTarget);
            ctx.getAuditDao().addAudit(new AuditRecord("approve", sender.getName(), storedTarget, "", System.currentTimeMillis()));

            // Send approval email
            var user = ctx.getUserByConfiguredUsername(storedTarget);
            if (user != null) {
                String email = (String) user.get("email");
                if (email != null && !email.isEmpty()) {
                    ctx.getMailService().sendReviewResult(email, storedTarget, true,
                            "", ctx.getConfigManager().getLanguage());
                }
            }

            sender.sendMessage("§6[VerifyMC] §aUser " + storedTarget + " approved.");
        } else {
            sender.sendMessage("§6[VerifyMC] §cFailed to approve user " + target);
        }
    }

    private void handleReject(CommandSender sender, String[] args) {
        if (!sender.hasPermission("verifymc.admin")) {
            sender.sendMessage("§6[VerifyMC] §cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§6[VerifyMC] §fUsage: /vmc reject <username> [reason]");
            return;
        }
        String target = args[1];
        String storedTarget = ctx.resolveStoredUsername(target);
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        boolean ok = storedTarget != null && ctx.getUserDao().updateUserStatus(storedTarget, "rejected", sender.getName());
        if (ok) {
            ctx.getAuditDao().addAudit(new AuditRecord("reject", sender.getName(), storedTarget, reason, System.currentTimeMillis()));

            var user = ctx.getUserByConfiguredUsername(storedTarget);
            if (user != null) {
                String email = (String) user.get("email");
                if (email != null && !email.isEmpty()) {
                    ctx.getMailService().sendReviewResult(email, storedTarget, false,
                            reason, ctx.getConfigManager().getLanguage());
                }
            }

            sender.sendMessage("§6[VerifyMC] §cUser " + storedTarget + " rejected.");
        } else {
            sender.sendMessage("§6[VerifyMC] §cFailed to reject user " + target);
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("verifymc.admin")) {
            sender.sendMessage("§6[VerifyMC] §cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§6[VerifyMC] §fUsage: /vmc delete <username>");
            return;
        }
        String target = args[1];
        String storedTarget = ctx.resolveStoredUsername(target);
        boolean ok = storedTarget != null && ctx.getUserDao().deleteUser(storedTarget);
        if (ok) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist remove " + storedTarget);
            ctx.getAuditDao().addAudit(new AuditRecord("delete", sender.getName(), storedTarget, "", System.currentTimeMillis()));
            sender.sendMessage("§6[VerifyMC] §aUser " + storedTarget + " deleted.");
        } else {
            sender.sendMessage("§6[VerifyMC] §cFailed to delete user " + target);
        }
    }

    private void handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("verifymc.admin")) {
            sender.sendMessage("§6[VerifyMC] §cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§6[VerifyMC] §fUsage: /vmc ban <username> [reason]");
            return;
        }
        String target = args[1];
        String storedTarget = ctx.resolveStoredUsername(target);
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        boolean ok = storedTarget != null && ctx.getUserDao().banUser(storedTarget);
        if (ok) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist remove " + storedTarget);
            ctx.getAuditDao().addAudit(new AuditRecord("ban", sender.getName(), storedTarget, reason, System.currentTimeMillis()));
            sender.sendMessage("§6[VerifyMC] §cUser " + storedTarget + " banned.");
        } else {
            sender.sendMessage("§6[VerifyMC] §cFailed to ban user " + target);
        }
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("verifymc.admin")) {
            sender.sendMessage("§6[VerifyMC] §cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§6[VerifyMC] §fUsage: /vmc unban <username>");
            return;
        }
        String target = args[1];
        String storedTarget = ctx.resolveStoredUsername(target);
        boolean ok = storedTarget != null && ctx.getUserDao().unbanUser(storedTarget);
        if (ok) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + storedTarget);
            ctx.getAuditDao().addAudit(new AuditRecord("unban", sender.getName(), storedTarget, "", System.currentTimeMillis()));
            sender.sendMessage("§6[VerifyMC] §aUser " + storedTarget + " unbanned.");
        } else {
            sender.sendMessage("§6[VerifyMC] §cFailed to unban user " + target);
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("verifymc.admin")) {
            sender.sendMessage("§6[VerifyMC] §cNo permission.");
            return;
        }
        String statusFilter = args.length > 1 ? args[1].toLowerCase() : "all";
        List<Map<String, Object>> users;
        if ("all".equals(statusFilter)) {
            users = ctx.getUserDao().getAllUsers();
        } else {
            users = ctx.getUserDao().getUsersByStatus(statusFilter);
        }

        sender.sendMessage("§6[VerifyMC] §f--- Users (" + statusFilter + ") ---");
        if (users.isEmpty()) {
            sender.sendMessage("§7  No users found.");
        } else {
            for (Map<String, Object> user : users) {
                String name = (String) user.getOrDefault("username", "?");
                String status = (String) user.getOrDefault("status", "?");
                sender.sendMessage("§7  " + name + " §f- §e" + status);
            }
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("verifymc.admin")) {
            sender.sendMessage("§6[VerifyMC] §cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§6[VerifyMC] §fUsage: /vmc info <username>");
            return;
        }
        String target = args[1];
        Map<String, Object> user = ctx.getUserByConfiguredUsername(target);
        if (user == null) {
            sender.sendMessage("§6[VerifyMC] §cUser not found: " + target);
            return;
        }

        sender.sendMessage("§6[VerifyMC] §f--- User Info ---");
        sender.sendMessage("§7  Username: §f" + user.getOrDefault("username", "?"));
        sender.sendMessage("§7  Email: §f" + user.getOrDefault("email", "?"));
        sender.sendMessage("§7  Status: §e" + user.getOrDefault("status", "?"));
    }

    private void handleVersion(CommandSender sender) {
        sender.sendMessage("§6[VerifyMC] §fVersion: " + ctx.getPlugin().getDescription().getVersion());
    }
}
