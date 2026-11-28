package app.morphe.patches.pinedrama.feedfilter

import app.morphe.Fixtures
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Following feed's restore of last run's saved list (#226): every retained build has one
 * method that checks the saved cache's updateTime, and the hook hands it a null cache when the
 * extension says so. Up to 47.7.3 the restore was handed the cache first and the hook nulls that
 * parameter at entry; 48.0.3's list refresh reads it itself, and every read goes through the
 * extension.
 */
class FollowingSavedFirstVideoTest {
    private val extension = "Lapp/morphe/extension/pinedrama/feedfilter/FollowingLatestFirst;"

    @Test
    fun `every retained fixture has one restore and the hook nulls the saved cache`() {
        val apks = Fixtures.apks()
        assertTrue("fixtures: ${apks.map { it.name }}", apks.containsAll(Fixtures.declared()))
        apks.forEach { apk ->
            val restores = load(apk).values.flatMap { it.methods }.filter { it.isRestore() }
            assertEquals("${apk.name}: restores ${restores.map { "${it.definingClass}->${it.name}" }}", 1, restores.size)
            val method = MutableMethod(restores.single())
            val handed = method.parameterTypes.firstOrNull()?.toString() == FOLLOWING_CACHE_DESCRIPTOR
            method.skipSavedFollowingFirstVideo()

            val instructions = method.implementation!!.instructions.toList()
            if (!handed) {
                // 48.0.3: the list refresh reads the cache itself, and each read passes through the extension.
                val reads = instructions.indices.filter { index ->
                    instructions[index].opcode == Opcode.INVOKE_STATIC &&
                        ((instructions[index] as ReferenceInstruction).reference as MethodReference).let {
                            it.parameterTypes.isEmpty() && it.returnType == FOLLOWING_CACHE_DESCRIPTOR
                        }
                }
                assertTrue("${apk.name}: no saved cache read", reads.isNotEmpty())
                reads.forEach { index ->
                    val cache = (instructions[index + 1] as OneRegisterInstruction).registerA
                    val wrap = (instructions[index + 2] as ReferenceInstruction).reference as MethodReference
                    assertEquals(extension, wrap.definingClass)
                    assertEquals("savedCache", wrap.name)
                    assertEquals(cache, (instructions[index + 2] as RegisterRangeInstruction).startRegister)
                    assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[index + 3].opcode)
                    assertEquals(cache, (instructions[index + 3] as OneRegisterInstruction).registerA)
                    assertEquals(Opcode.CHECK_CAST, instructions[index + 4].opcode)
                }
                return@forEach
            }
            val call = (instructions[0] as ReferenceInstruction).reference as MethodReference
            assertEquals(extension, call.definingClass)
            assertEquals("skipSavedFirstVideo", call.name)
            assertEquals(Opcode.MOVE_RESULT, instructions[1].opcode)
            assertEquals(Opcode.IF_EQZ, instructions[2].opcode)
            assertEquals(Opcode.CONST_4, instructions[3].opcode)
            val write = instructions[4] as TwoRegisterInstruction
            assertEquals(Opcode.MOVE_OBJECT_FROM16, write.opcode)
            // p0 is the receiver, so the cache is the register after it: four parameter registers in all.
            assertEquals("${apk.name}: the cache register", method.implementation!!.registerCount - 3, write.registerA)
            assertEquals((instructions[3] as OneRegisterInstruction).registerA, write.registerB)
        }
    }

    @Test
    fun `a method that does not take the cache first stops the patch`() {
        val method = MutableMethod(
            ImmutableMethod(
                "LX/Restore;",
                "restore",
                listOf(ImmutableMethodParameter("Ljava/util/List;", null, null)),
                "V",
                AccessFlags.PUBLIC.value,
                null,
                null,
                ImmutableMethodImplementation(3, listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)), null, null),
            ),
        )
        val error = assertThrows(PatchException::class.java) { method.skipSavedFollowingFirstVideo() }
        assertTrue(error.message!!, error.message!!.contains("does not take the saved cache first"))
    }

    private fun Method.isRestore(): Boolean = returnType == "V" && isFollowingCacheRestore()

    private fun load(apk: File): Map<String, ClassDef> {
        val container = Fixtures.dexContainer(apk, Opcodes.getDefault())
        val classes = HashMap<String, ClassDef>()
        container.dexEntryNames.forEach { entry ->
            container.getEntry(entry)!!.dexFile.classes.forEach { classes.putIfAbsent(it.type, it) }
        }
        return classes
    }
}
