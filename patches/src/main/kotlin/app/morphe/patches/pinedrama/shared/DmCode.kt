/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.pinedrama.shared

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException

/** The chat screen. The manifest names it on every build; the class is there only with the DM code. */
internal const val DM_CHAT_SCREEN_CLASS =
    "Lcom/ss/android/ugc/aweme/im/sdk/chat/ui/singleactivity/ChatShellActivity;"

/**
 * Whether this APK carries [type], a class of PineDrama's DM code.
 *
 * <p>Up to 47.7.3 the chat screen, the typing indicator, the activity status report and the DM
 * sticker panel were in the base APK or in df_a_dex, which the APKMirror bundle carried. 48.0.3
 * keeps only their interfaces and stand-in implementations in the base APK (the manifest still
 * names the chat screen) and ships the code itself in a feature module the APKMirror bundle
 * doesn't include, so an APK patched from that bundle runs without it. A declared build must have
 * the class, so a missing one fails [patch] there; any other build leaves [what] out with a note.
 */
internal fun BytecodePatchContext.shipsDmClass(patch: String, what: String, type: String): Boolean {
    if (classDefByOrNull(type) != null) return true
    if (packageMetadata.buildName in declaredVersions()) {
        throw PatchException("$patch: $type is missing, so this APK has no $what.")
    }
    println("[$patch] Left out $what on ${packageMetadata.buildName}: this APK doesn't carry PineDrama's DM code ($type).")
    return false
}
