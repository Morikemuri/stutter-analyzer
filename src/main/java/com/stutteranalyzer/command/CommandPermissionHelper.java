package com.stutteranalyzer.command;

import com.stutteranalyzer.config.SAConfig;
import net.minecraft.commands.CommandSourceStack;

public class CommandPermissionHelper {

    public static boolean canViewBasicStatus(CommandSourceStack src) {
        if (SAConfig.INSTANCE.allowPlayersBasicStatus.get()) return true;
        return src.hasPermission(2);
    }

    public static boolean canViewServerReports(CommandSourceStack src) {
        return src.hasPermission(SAConfig.INSTANCE.serverReportPermissionLevel.get());
    }

    public static boolean canReloadConfig(CommandSourceStack src) {
        return src.hasPermission(SAConfig.INSTANCE.configReloadPermissionLevel.get());
    }

    public static boolean canSubmitReports(CommandSourceStack src) {
        return src.hasPermission(SAConfig.INSTANCE.submitReportPermissionLevel.get());
    }

    public static boolean canUseDebug(CommandSourceStack src) {
        return SAConfig.INSTANCE.debug.get()
            && src.hasPermission(SAConfig.INSTANCE.debugPermissionLevel.get());
    }

    public static boolean canExportOwnClientReport(CommandSourceStack src) {
        if (SAConfig.INSTANCE.allowPlayersExportOwnClientReports.get()) return true;
        return src.hasPermission(2);
    }

    public static boolean canManageGuards(CommandSourceStack src) {
        return src.hasPermission(SAConfig.INSTANCE.guardPermissionLevel.get());
    }
}
