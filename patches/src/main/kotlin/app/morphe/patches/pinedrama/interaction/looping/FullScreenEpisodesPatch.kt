/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.pinedrama.interaction.looping

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.pinedrama.misc.extension.sharedExtensionPatch
import app.morphe.patches.pinedrama.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.pinedrama.misc.settings.configurableBytecodePatch as bytecodePatch
import app.morphe.patches.pinedrama.misc.settings.settingsPatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.addInstruction
import app.morphe.util.addInstructions
import app.morphe.util.addInstructionsWithLabels
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.implementationOrPatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/pinedrama/interaction/LandscapeEpisodes;"
private const val OBJECT = "Ljava/lang/Object;"
private const val LIST = "Ljava/util/List;"
private const val PATCH = "Episodes in full screen"
internal const val LANDSCAPE_ENTRANCE =
    "Lcom/ss/android/ugc/aweme/feed/landscape/LandscapeEntranceAssem;"
internal const val LANDSCAPE_VIEW_MODEL =
    "Lcom/ss/android/ugc/aweme/feed/landscape/viewmodels/LandscapeFeedViewModel;"
internal const val LANDSCAPE_LOAD_LOGIC =
    "Lcom/ss/android/ugc/aweme/feed/landscape/component/LandscapeFeedDataLoadLogicComponent;"
internal const val LANDSCAPE_ACTIVITY =
    "Lcom/ss/android/ugc/aweme/feed/landscape/LandscapeFeedActivity;"

/** Runs when the "Full screen" pill is tapped, and reads the screen's own view model. */
internal object LandscapeEntranceFingerprint : Fingerprint(
    definingClass = LANDSCAPE_ENTRANCE,
    returnType = "V",
    parameters = listOf("I", "I", "I", "Ljava/lang/String;"),
)

/** The one-item list the viewer opens with. */
internal object LandscapeSeedListFingerprint : Fingerprint(
    definingClass = LANDSCAPE_VIEW_MODEL,
    returnType = LIST,
    parameters = emptyList(),
)

internal object LandscapeLoadMoreFingerprint : Fingerprint(
    definingClass = LANDSCAPE_LOAD_LOGIC,
    returnType = "V",
    parameters = listOf("I", "I", "Z"),
    strings = listOf("can't load more"),
)

internal object LandscapeLoadPreviousFingerprint : Fingerprint(
    definingClass = LANDSCAPE_LOAD_LOGIC,
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("can't load latest"),
)

internal object LandscapeActivityFinishFingerprint : Fingerprint(
    definingClass = LANDSCAPE_ACTIVITY,
    name = "finish",
    returnType = "V",
    parameters = emptyList(),
)

internal const val LANDSCAPE_PAGER_ABILITY =
    "Lcom/ss/android/ugc/aweme/feed/landscape/component/ability/LandscapeFeedViewPagerAbility;"
internal const val LANDSCAPE_PAGER_COMPONENT =
    "Lcom/ss/android/ugc/aweme/feed/landscape/component/LandscapeFeedViewPagerComponent;"
private const val LANDSCAPE_LOAD_MORE_ABILITY =
    "Lcom/ss/android/ugc/aweme/feed/landscape/component/ability/LandscapeLoadMoreAbility;"
private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"

/**
 * The viewer members the extension calls by reflection. R8 gives them new names on every build
 * (`j4` and `id` on 47.7.3 are `q4` and `Hd` on 48.0.3), so each is found by what it does.
 */
internal class ViewerMembers(
    val append: String,
    val prepend: String,
    val setAutomatic: String,
    val loadEnded: String,
    val pagerAbility: String,
    val currentItem: String,
)

private fun Method.parameterNames() = parameterTypes.map { it.toString() }

private fun Method.body(): List<Instruction> = implementation?.instructions?.toList() ?: emptyList()

/** The viewer's static `(List, List)List`, which keeps the episodes of the first it lacks. */
internal fun Instruction.isListDifference(viewer: String): Boolean {
    if (opcode != Opcode.INVOKE_STATIC) return false
    val call = getReference<MethodReference>() ?: return false
    return call.definingClass == viewer && call.returnType == LIST &&
        call.parameterTypes.map { it.toString() } == listOf(LIST, LIST)
}

internal fun viewerMembers(viewer: ClassDef, pager: ClassDef): ViewerMembers {
    fun one(what: String, found: List<Method>): String =
        found.singleOrNull()?.name ?: throw PatchException("$PATCH: the viewer has ${found.size} $what, not one.")
    val listAndFlag = viewer.methods.filter { it.returnType == "V" && it.parameterNames() == listOf(LIST, "Z") }
    // The prepend works out which episodes are new itself. The append hands its list straight on
    // to the viewer's (List, boolean, boolean) merge, which does that for both directions.
    val prepend = one("prepends", listAndFlag.filter { method -> method.body().any { it.isListDifference(viewer.type) } })
    val append = one("appends", listAndFlag.filter { method ->
        val code = method.body()
        code.size == 3 && code[1].getReference<MethodReference>()?.let {
            it.definingClass == viewer.type && it.parameterTypes.map { type -> type.toString() } == listOf(LIST, "Z", "Z")
        } == true
    })
    // The flag setter only stores its boolean.
    val setAutomatic = one("load flags", viewer.methods.filter { method ->
        val code = method.body()
        method.returnType == "V" && method.parameterNames() == listOf("Z") && code.size == 2 &&
            code[0].opcode == Opcode.IPUT_BOOLEAN
    })
    // The viewer passes a failed load to its load-more ability from two methods that do the same;
    // the one named like the ability's own is taken.
    val enders = viewer.methods.filter { method ->
        method.returnType == "V" && method.parameterNames() == listOf("Ljava/lang/Exception;") &&
            method.body().any { it.endsLoad() }
    }
    val loadEnded = enders.firstOrNull { method ->
        method.body().any { it.endsLoad() && it.getReference<MethodReference>()!!.name == method.name }
    }?.name ?: enders.minByOrNull { it.name }?.name
        ?: throw PatchException("$PATCH: the viewer no longer tells its load-more ability a load failed.")
    val pagerAbility = one("pager abilities", viewer.methods.filter {
        it.parameterTypes.isEmpty() && it.returnType == LANDSCAPE_PAGER_ABILITY
    })
    // The current video is the item at the pager's own position: a ()I read, then an (I)Aweme one.
    val currentItem = pager.methods.filter { method ->
        val code = method.body()
        method.parameterTypes.isEmpty() && method.returnType == AWEME && code.size == 5 &&
            code[0].getReference<MethodReference>()?.let {
                it.definingClass == pager.type && it.parameterTypes.isEmpty() && it.returnType == "I"
            } == true &&
            code[2].getReference<MethodReference>()?.let {
                it.definingClass == pager.type && it.parameterTypes.map { type -> type.toString() } == listOf("I") &&
                    it.returnType == AWEME
            } == true
    }.singleOrNull()?.name ?: throw PatchException("$PATCH: the pager no longer reads its current video.")
    return ViewerMembers(append, prepend, setAutomatic, loadEnded, pagerAbility, currentItem)
}

private fun Instruction.endsLoad(): Boolean =
    opcode == Opcode.INVOKE_INTERFACE && getReference<MethodReference>()?.let {
        it.definingClass == LANDSCAPE_LOAD_MORE_ABILITY && it.returnType == "V" &&
            it.parameterTypes.map { type -> type.toString() } == listOf("Ljava/lang/Exception;")
    } == true

/** The entrance's read of the screen's view model, the only no-argument getViewModel it makes. */
internal fun isViewModelRead(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.INVOKE_INTERFACE) return false
    val reference = instruction.getReference<MethodReference>() ?: return false
    return reference.name == "getViewModel" && reference.returnType == OBJECT &&
        reference.parameterTypes.isEmpty()
}

val fullScreenEpisodesPatch = bytecodePatch(
    name = "Episodes in full screen",
    description = "Lets you swipe between a drama's episodes in full screen, and moves on to " +
        "the next episode when one ends instead of looping it. Starts on. Turn it off in " +
        "HushPine settings > Playback.",
    default = true,
) {
    category("Playback")
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.pineDrama())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/pinedrama/settings/SettingsStatus;->enableLandscapeEpisodes()V",
        )

        val entrance = LandscapeEntranceFingerprint.method
        val entranceCode = entrance.implementationOrPatchException(PATCH).instructions.toList()
        val read = entranceCode.indexOfFirst(::isViewModelRead)
        if (read < 0) throw PatchException("$PATCH: the entrance no longer reads a view model.")
        val model = entrance.getInstruction<OneRegisterInstruction>(read + 1).registerA
        entrance.addInstruction(
            read + 2,
            "invoke-static {p0, v$model}, $EXTENSION->remember($OBJECT$OBJECT)V",
        )

        // The new-instance just before the return hands the list to a state write, which has to
        // get the same list the viewer does. The empty path jumps to that new-instance from below
        // and stays on the original instruction, so an empty list never reaches the extension.
        val seed = LandscapeSeedListFingerprint.method
        val seedCode = seed.implementationOrPatchException(PATCH).instructions.toList()
        val returned = seedCode.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
        if (returned < 0) throw PatchException("$PATCH: the seed list has no return.")
        val handOff = (returned downTo 0).firstOrNull { seedCode[it].opcode == Opcode.NEW_INSTANCE }
            ?: throw PatchException("$PATCH: the seed list has no state write.")
        val list = seed.getInstruction<OneRegisterInstruction>(returned).registerA
        seed.addInstructions(
            handOff,
            """
                invoke-static {v$list}, $EXTENSION->episodes($LIST)$LIST
                move-result-object v$list
            """,
        )

        // Both start by reading a flag into v0, so v0 is free at entry.
        val more = LandscapeLoadMoreFingerprint.method
        more.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0, p1, p2, p3}, $EXTENSION->loadMore(${OBJECT}IIZ)Z
                move-result v0
                if-eqz v0, :morphe_native_more
                return-void
            """,
            ExternalLabel("morphe_native_more", more.getInstruction(0)),
        )
        val previous = LandscapeLoadPreviousFingerprint.method
        previous.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0}, $EXTENSION->loadPrevious($OBJECT)Z
                move-result v0
                if-eqz v0, :morphe_native_previous
                return-void
            """,
            ExternalLabel("morphe_native_previous", previous.getInstruction(0)),
        )

        LandscapeActivityFinishFingerprint.method.addInstruction(
            0,
            "invoke-static {p0}, $EXTENSION->leaving($OBJECT)V",
        )

        // The extension reaches these by reflection, so it gets this build's names.
        val pager = classDefByOrNull(LANDSCAPE_PAGER_COMPONENT)
            ?: throw PatchException("$PATCH: the full screen pager is gone.")
        val members = viewerMembers(LandscapeLoadMoreFingerprint.classDef, pager)
        val extension = mutableClassDefBy(EXTENSION)
        val original = extension.methods.singleOrNull { it.name == "useBuildNames" && it.parameterTypes.isEmpty() }
            ?: throw PatchException("$PATCH: the extension has no name bridge.")
        val bridge = original.cloneMutable(additionalRegisters = 6)
        extension.methods.remove(original)
        extension.methods.add(bridge)
        val names = listOf(
            members.append, members.prepend, members.setAutomatic,
            members.loadEnded, members.pagerAbility, members.currentItem,
        )
        bridge.addInstructions(
            0,
            names.withIndex().joinToString("\n") { (register, name) -> "const-string v$register, \"$name\"" } +
                "\ninvoke-static/range {v0 .. v5}, $EXTENSION->useNames(" + "Ljava/lang/String;".repeat(6) + ")V",
        )
    }
}
