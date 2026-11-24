/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.pinedrama.interaction.favorites

import app.morphe.patcher.patch.PatchException
import app.morphe.patches.pinedrama.misc.extension.sharedExtensionPatch
import app.morphe.patches.pinedrama.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.pinedrama.misc.settings.configurableBytecodePatch as bytecodePatch
import app.morphe.patches.pinedrama.misc.settings.settingsPatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.addInstruction
import app.morphe.util.getReference
import app.morphe.util.implementationOrPatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val LIST_TOOLS = "Lapp/morphe/extension/pinedrama/favorites/ListTools;"

val listToolsPatch = bytecodePatch(
    name = "List search and tap to top",
    description = "Adds a search bar above the History and Favorites lists on your profile that " +
        "finds a title anywhere in them, and tapping the tab you're already on takes its list " +
        "back to the top. On by default. Turn either off in HushPine settings.",
    default = true,
) {
    category("Settings")
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.pineDrama())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/pinedrama/settings/SettingsStatus;->enableListTools()V",
        )

        // The same refresh gate Keep list position answers: each History and Favorites page
        // hands it the page's list as it's built, which is the one moment every list passes by.
        val destroy = HistoryListPageDestroyFingerprint.method
        val gateType = destroy.implementationOrPatchException("List search and tap to top").instructions
            .firstOrNull { it.opcode == Opcode.IGET_OBJECT }
            ?.getReference<FieldReference>()?.type
            ?: throw PatchException("List search and tap to top: the History page holds no refresh gate.")
        val watch = mutableClassDefBy(gateType).methods.singleOrNull { it.isRefreshGateWatch() }
            ?: throw PatchException("List search and tap to top: $gateType has no single watch method.")

        watch.addInstruction(0, "invoke-static {p1}, $LIST_TOOLS->attach(Landroid/view/View;)V")
    }
}
