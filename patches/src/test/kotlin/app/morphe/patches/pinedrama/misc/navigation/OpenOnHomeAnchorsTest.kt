package app.morphe.patches.pinedrama.misc.navigation

import app.morphe.Fixtures
import app.morphe.takes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #6: the patch answers the default landing tab, so every Discover a start can see must come
 * from it, on every declared build. Callers outside its class ask it straight or ask the override
 * reader, which falls back to it. The one exception is the lambda behind the default's lazy holder,
 * which asks the raw calculation and answers only the default. Discover's landing rule is held to that by name, since that rule
 * is what sent the reporter's starts to Discover, and every write of the override must be Drama, or
 * a start reading the override would still land on Discover.
 */
class OpenOnHomeAnchorsTest {
    private val discoverRule = "Lcom/ss/android/ugc/aweme/homepage/tab/DefaultSeriesDiscoverTabProtocol;"

    @Test
    fun `every landing tab a start reads comes from the hooked default or a Drama override`() {
        Fixtures.forEachDeclared { apk ->
            val container = Fixtures.dexContainer(apk, Opcodes.getDefault())
            val classes = HashMap<String, ClassDef>()
            for (entry in container.dexEntryNames) {
                for (classDef in container.getEntry(entry)!!.dexFile.classes) classes[classDef.type] = classDef
            }

            val owners = classes.values.filter { classDef ->
                classDef.methods.any { LandingTabOldUserFingerprint.takes(it, classDef) }
            }
            assertEquals("old-user check: ${owners.map { it.type }}", 1, owners.size)
            val readerClass = owners.single()
            val tabTypes = readerClass.staticReturnTypes().filter { classes[it]?.isLandingTabEnum() == true }
            assertEquals("landing tab enum: $tabTypes", 1, tabTypes.size)
            val tabType = tabTypes.single()
            val default = readerClass.methods.filter { it.isLandingTabDefault(tabType) }
            assertEquals("default $tabType in ${readerClass.type}", 1, default.size)
            val reader = readerClass.methods.filter { it.isLandingTabReader(tabType) }
            assertEquals("override reader of $tabType in ${readerClass.type}", 1, reader.size)
            val defaultName = default.single().name
            val readerName = reader.single().name
            val readerCalls = reader.single().implementation!!.instructions.mapNotNull {
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.takeIf { call -> call.definingClass == readerClass.type }?.name
            }
            assertEquals("the override reader's fallback", listOf(defaultName), readerCalls)
            val lazyBodies = readerClass.lazyHolderBodies()

            val asked = HashSet<String>()
            val overrideWrites = ArrayList<String>()
            for (classDef in classes.values) {
                for (method in classDef.methods) {
                    val instructions = method.implementation?.instructions?.toList() ?: continue
                    for ((index, instruction) in instructions.withIndex()) {
                        val reference = (instruction as? ReferenceInstruction)?.reference
                        if (instruction.opcode == Opcode.SPUT_OBJECT && reference is FieldReference &&
                            reference.definingClass == readerClass.type && reference.type == tabType
                        ) {
                            overrideWrites += "${classDef.type}->${method.name} " + writtenValue(instructions, index)
                        }
                        val call = reference as? MethodReference ?: continue
                        if (classDef.type == readerClass.type) continue
                        if (call.definingClass != readerClass.type || call.returnType != tabType) continue
                        // The lazy holder's own body works the default out, and only the default reads the holder.
                        if ("${classDef.type}->${method.name}" in lazyBodies) continue
                        assertTrue("${classDef.type}->${method.name} asks ${call.name}", call.name in setOf(defaultName, readerName))
                        asked += classDef.type
                    }
                }
            }
            assertTrue("Discover's landing rule no longer asks the default: $asked", discoverRule in asked)
            assertTrue("no write of the override found", overrideWrites.isNotEmpty())
            overrideWrites.forEach { assertTrue("override written with something other than Drama: $it", it.endsWith(" DRAMA_TAB")) }
        }
    }

    /** What the register an override write stores last held, read back to its last write in a straight line. */
    private fun writtenValue(instructions: List<Instruction>, index: Int): String {
        val register = (instructions[index] as OneRegisterInstruction).registerA
        for (i in index - 1 downTo 0) {
            val instruction = instructions[i]
            if (!instruction.opcode.setsRegister() || (instruction as? OneRegisterInstruction)?.registerA != register) continue
            if (instruction.opcode == Opcode.SGET_OBJECT) return ((instruction as ReferenceInstruction).reference as FieldReference).name
            return instruction.opcode.toString()
        }
        return "unknown"
    }

    /**
     * The merged Kotlin lambdas this class's init builds for its lazy holder, as `Type->invoke$n`.
     * On 48.0.3 the default's lazy path (kt_lazy_load_exp) gets its answer from
     * `AFtS122S0000000_13.get$arr$(940)`, whose `invoke$940` asks the raw calculation itself.
     */
    private fun ClassDef.lazyHolderBodies(): Set<String> {
        val instructions = methods.firstOrNull { it.name == "<clinit>" }?.implementation?.instructions?.toList() ?: return emptySet()
        return instructions.withIndex().mapNotNull { (index, instruction) ->
            val call = ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                ?.takeIf { instruction.opcode == Opcode.INVOKE_STATIC && it.name == "get\$arr\$" && it.parameterTypes.map(CharSequence::toString) == listOf("I") }
                ?: return@mapNotNull null
            val argument = (instruction as FiveRegisterInstruction).registerC
            val case = instructions.subList(0, index).lastOrNull { (it as? OneRegisterInstruction)?.registerA == argument }
                as? NarrowLiteralInstruction ?: return@mapNotNull null
            "${call.definingClass}->invoke\$${case.narrowLiteral}"
        }.toSet()
    }
}
