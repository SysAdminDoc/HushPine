/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.pinedrama.interaction.quickactions

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val IM_SHARE_SERVICE =
    "Lcom/ss/android/ugc/aweme/im/sharepanel/impl/service/IMShareService;"

/**
 * The gate that says whether a long press on the share button opens quick share.
 *
 * <p>It was `LX/0BJV;` and it is `LX/09Nm;` on 46.7.3 and `LX/09ON;` on 46.8.3. There is nothing
 * in the class to recognise: it is one of hundreds of two-member gates PineDrama generates around a
 * lazily read AB integer, and the key it reads sits behind a lambda R8 has merged into a shared
 * group, where following it means decoding a packed switch that is numbered differently on each
 * build.
 *
 * <p>What names it is who asks. The method that asks has one body on every retained build: read
 * the gate, compare it to one, read it again and compare it to two, ask a second `()Z` gate, and
 * answer. Fourteen instructions, and no other `()Z` in any of the four builds has them. It sat
 * on `IMShareService` under that name up to 46.8.3 (`LJIIIIZZ`, then `LJIIIZ`); 46.9.3 lets R8
 * rename the service (`LX/00QV;`) and the body is what is left to go on. Where the name still
 * exists the two have to agree, so a build that kept the class and moved the question is refused
 * rather than guessed.
 *
 * <p>48.0.3 ships the asking service with the DM code, outside the base APK, and the gate (`LX/0IOE;`)
 * reads its key on an eager path of its own when the lazy one is off, so there the key names it.
 */
private val GATE_CALLER = listOf(
    Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.CONST_4, Opcode.CONST_4, Opcode.IF_EQ,
    Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.CONST_4, Opcode.IF_NE,
    Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.IF_NEZ, Opcode.CONST_4, Opcode.RETURN,
)

private object LongPressQuickShareGateCallerFingerprint : Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, _ ->
        method.implementation?.instructions?.map { it.opcode } == GATE_CALLER
    },
)

internal const val QUICK_SHARE_KEY = "im_long_press_share_button_to_quick_share"

/** The gate on a build whose eager path loads its key in the gate itself (48.0.3). */
private object LongPressQuickShareKeyedGateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "I",
    parameters = emptyList(),
    strings = listOf(QUICK_SHARE_KEY),
)

private val LAZY_INT_BOXES = setOf("Ljava/lang/Number;", "Ljava/lang/Integer;")

internal fun BytecodePatchContext.resolveLongPressQuickShareGate(): MutableMethod {
    val keyed = LongPressQuickShareKeyedGateFingerprint.matchAllOrNull().orEmpty()
    if (keyed.size > 1) {
        throw PatchException("Long press quick share: ${keyed.size} static ()I methods load $QUICK_SHARE_KEY.")
    }
    keyed.singleOrNull()?.let { return it.method.alsoReadsLazyInt() }
    val matches = LongPressQuickShareGateCallerFingerprint.matchAllOrNull().orEmpty()
    val caller = matches.singleOrNull()?.originalMethod ?: throw PatchException(
        "Long press quick share: expected one method with the gate caller's shape, found " +
            "${matches.size}.",
    )
    if (classDefByOrNull(IM_SHARE_SERVICE) != null && caller.definingClass != IM_SHARE_SERVICE) {
        throw PatchException(
            "Long press quick share: the gate caller is ${caller.definingClass}->${caller.name} " +
                "on a build that still has $IM_SHARE_SERVICE.",
        )
    }
    val called = caller.implementation!!.instructions
        .filter { it.opcode == Opcode.INVOKE_STATIC || it.opcode == Opcode.INVOKE_STATIC_RANGE }
        .mapNotNull { it.getReference<MethodReference>() }
        .filter { it.returnType == "I" && it.parameterTypes.isEmpty() }
        .distinctBy { "${it.definingClass}->${it.name}" }
    if (called.size != 1) {
        throw PatchException(
            "Long press quick share: expected the gate caller to call one static ()I gate, " +
                "found ${called.size}.",
        )
    }
    val gate = called.single()
    val method = mutableClassDefBy(gate.definingClass).methods.singleOrNull {
        it.name == gate.name && it.returnType == "I" && it.parameterTypes.isEmpty()
    } ?: throw PatchException(
        "Long press quick share: ${gate.definingClass} has no ${gate.name}()I to override.",
    )
    return method.alsoReadsLazyInt()
}

/**
 * Who calls it, or the key it loads, says which method; this says it is the kind of method the
 * patch rewrites. A build that stopped calling the gate from there but left any other static ()I
 * would otherwise resolve to that one and have its return overridden. 48.0.3 unwraps an Integer.
 */
private fun MutableMethod.alsoReadsLazyInt(): MutableMethod {
    val references = implementation?.instructions
        ?.mapNotNull { it.getReference<MethodReference>() }
        ?: emptyList()
    val readsLazyValue = references.any {
        it.name == "getValue" && it.returnType == "Ljava/lang/Object;" && it.parameterTypes.isEmpty()
    }
    val unwrapsInt = references.any {
        it.definingClass in LAZY_INT_BOXES && it.name == "intValue" && it.returnType == "I"
    }
    if (!readsLazyValue || !unwrapsInt) {
        throw PatchException(
            "Long press quick share: $definingClass->$name does not read a lazy " +
                "value and unwrap it as an int, so it is not the gate.",
        )
    }
    return this
}
