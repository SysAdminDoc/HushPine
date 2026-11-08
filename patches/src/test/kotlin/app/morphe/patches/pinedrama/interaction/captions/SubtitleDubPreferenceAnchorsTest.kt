/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.pinedrama.interaction.captions

import app.morphe.Fixtures
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every place the patch reads off a declared build's dex, walked the way the patch walks it. The
 * registers matter here: a hook that reads the wrong one still verifies and just picks nothing.
 */
class SubtitleDubPreferenceAnchorsTest {
    private fun code(method: Method): List<Instruction> = method.implementation!!.instructions.toList()

    private fun Method.named(vararg types: String) = parameterTypes.map { it.toString() } == types.toList()

    private fun forEachBuild(check: (Map<String, ClassDef>, List<ClassDef>) -> Unit) {
        Fixtures.forEachDeclared { apk ->
            val container = Fixtures.dexContainer(apk, Opcodes.getDefault())
            val classes = HashMap<String, ClassDef>()
            for (entry in container.dexEntryNames) {
                for (classDef in container.getEntry(entry)!!.dexFile.classes) classes[classDef.type] = classDef
            }
            check(classes, classes.values.toList())
        }
    }

    private fun method(classes: Map<String, ClassDef>, reference: MethodReference): Method =
        classes.getValue(reference.definingClass).methods.first {
            it.name == reference.name && it.parameterTypes.map { p -> p.toString() } ==
                reference.parameterTypes.map { p -> p.toString() } && it.returnType == reference.returnType
        }

    private fun holding(all: List<ClassDef>, vararg text: String): Method = all.asSequence()
        .flatMap { it.methods.asSequence() }
        .first { method ->
            val strings = method.implementation?.instructions
                ?.mapNotNull { it.getReference<StringReference>()?.string }?.toSet() ?: emptySet()
            text.all { it in strings }
        }

    @Test
    fun `both subtitle pick sites build a config from registers a short invoke can name`() = forEachBuild { classes, _ ->
        val prepare = classes.getValue(PREPARE_CAPTION_UTIL).methods.first { method ->
            method.named(AWEME_TYPE) && code(method).any { instruction ->
                instruction.opcode == Opcode.INVOKE_STATIC &&
                    instruction.getReference<MethodReference>()?.parameterTypes?.size == 2
            }
        }
        val config = prepare.returnType
        val service = classes.getValue(CAPTION_SERVICE).methods.first {
            it.returnType == config && it.named(AWEME_TYPE)
        }
        val paths = listOf(
            method(classes, configFromScene(code(prepare), config)),
            method(classes, staticFromVideo(code(service), config)),
        )
        for (path in paths) {
            val site = configSite(path)
            assertTrue("${path.name} registers", site.video < 16 && site.model < 16 && site.id < 16)
            assertTrue("${path.name} distinct registers", setOf(site.video, site.model, site.id).size == 3)
            assertTrue("${path.name} v0 is scratch here", 0 !in setOf(site.video, site.model, site.id))
            assertTrue("${path.name} v1 is scratch here", 1 !in setOf(site.video, site.model, site.id))
        }
    }

    @Test
    fun `the log line names the two caption checks and the hand choice`() = forEachBuild { classes, all ->
        val log = code(holding(all, "SubConfigController, getConfig, aweme: ", CAPTION_USER_SETTING))
        val setting = checkAfter(log, CAPTION_USER_SETTING)
        assertEquals("the setting check takes only the video", 1, setting.parameterTypes.size)
        assertEquals("still the one register body", 1, method(classes, setting).implementation!!.registerCount)
        val hide = checkAfter(log, HIDE_FOREIGN_ORIGINAL)
        assertEquals(2, hide.parameterTypes.size)
        val choice = handChoice(method(classes, hide))
        val names = classes.getValue(choice.lookup.returnType).fields.map { it.name }
        assertTrue("hand choice enum: $names", "OVERRIDE_TO_TRANSLATED" in names && "OVERRIDE_TO_ORIGINAL" in names)

        val writers = captionSwitchWriters(classes.getValue(CAPTION_ASSEM).methods, setting)
        val keva = classes.getValue(CAPTION_KEVA_SERVICE)
        for (writer in writers) {
            val impl = keva.methods.first { it.name == writer && it.named("Z") }
            assertTrue("$writer can name p1", impl.implementation!!.registerCount <= 16)
        }
    }

    @Test
    fun `the dub decision keeps its target, switch and skip registers`() = forEachBuild { classes, all ->
        val user = holding(all, "mayConfigToPlayTranslatedVideo: enterFrom=")
        val decision = method(classes, staticFromVideo(code(user), user.returnType))
        val sites = dubDecisionSites(decision)
        assertTrue("target comes first", sites.targetAt < sites.joinAt)
        val video = decision.implementation!!.registerCount - 1
        assertTrue(video < 16 && sites.target < 16 && sites.toggle < 16 && sites.skipList < 16)
        assertTrue("distinct registers", setOf(sites.target, sites.toggle, sites.skipList, video).size == 4)
        val after = code(decision)[sites.joinAt + 1]
        assertEquals("v0 is written straight after the join's flag read", Opcode.MOVE_RESULT, after.opcode)
    }

    @Test
    fun `the audio switch reads a language and the hand choice`() = forEachBuild { classes, _ ->
        val switching = classes.getValue(AUTO_DUBBING_SERVICE).methods.first { method ->
            method.named(AWEME_TYPE) && method.returnType == "V" && code(method).any {
                it.getReference<StringReference>()?.string == "updateAutoDubbingAudioLanguage aid:"
            }
        }
        val site = dubSwitchSite(switching)
        val video = switching.implementation!!.registerCount - 1
        assertTrue(video < 16 && site.language < 16 && site.choice < 16)
        assertTrue("distinct registers", setOf(video, site.language, site.choice).size == 3)
    }

    @Test
    fun `one service check answers the dub label and it has room for the hand choice`() = forEachBuild { classes, _ ->
        val label = classes.getValue(AUTO_DUBBING_SERVICE).methods.filter { method ->
            method.named(AWEME_TYPE) && method.returnType == "Z" && code(method).any {
                it.opcode == Opcode.INVOKE_STATIC && it.getReference<MethodReference>()?.let { call ->
                    call.returnType == "Z" && call.parameterTypes.map { p -> p.toString() } == listOf(AWEME_TYPE, "Z")
                } == true
            }
        }.single()
        val registers = label.implementation!!.registerCount
        assertTrue("two locals for the hand choice", registers - 2 >= 2)
        assertTrue("the video fits a short invoke", registers - 1 < 16)
    }

    private fun overlayTarget(all: List<ClassDef>): Method = all.asSequence()
        .flatMap { it.methods.asSequence() }
        .first { method ->
            val body = method.implementation?.instructions?.toList() ?: return@first false
            method.parameterTypes.size == 4 && method.parameterTypes[2].toString() == "Ljava/lang/Object;" &&
                body.any { it.getReference<FieldReference>()?.name == "TRANSLATED" } &&
                body.any {
                    it.getReference<MethodReference>()?.definingClass ==
                        "Lcom/ss/android/ugc/aweme/translation/service/ITranslationKevaService;"
                }
        }

    @Test
    fun `both overlay requests leave two free registers and the target reads every request field`() =
        forEachBuild { classes, all ->
            val target = overlayTarget(all)
            val sites = overlaySites(target)
            val body = code(target)
            for (site in sites) {
                val registers = setOf(site.video, site.request, site.freeA, site.freeB)
                assertEquals("distinct registers at ${site.at}", 4, registers.size)
                assertTrue("short invokes can name them", registers.all { it < 16 })
                assertEquals("the method answers right after", Opcode.RETURN_OBJECT, body[site.at + 2].opcode)
                val returned = body[site.at + 2] as OneRegisterInstruction
                assertEquals("the free answer register is the one returned", site.freeB, returned.registerA)
            }
            val building = body[sites.first().at].getReference<MethodReference>()!!
            val fields = overlayFields(method(classes, building))
            assertEquals("I", fields.id.type)
            assertEquals("Ljava/lang/String;", fields.language.type)
            assertEquals("Ljava/lang/String;", fields.path.type)
            assertEquals("Z", fields.translated.type)
            assertEquals("Z", fields.realtime.type)
            val owner = classes.getValue(building.definingClass)
            for (field in listOf(fields.id, fields.language, fields.path, fields.translated, fields.realtime)) {
                val declared = owner.fields.first { it.name == field.name }
                assertEquals("${field.name} can be written from outside", 0, declared.accessFlags and FINAL_OR_PRIVATE)
            }
        }

    @Test
    fun `the stale verdict is read after every path sets it`() = forEachBuild { _, all ->
        val display = holding(all, "calculating UI display state.  ", STALE_TARGET_HIDDEN)
        val gate = staleGate(display)
        val body = code(display)
        val set = body[gate.at - 1]
        assertEquals(Opcode.CONST_4, set.opcode)
        assertEquals("the verdict's register", gate.flag, (set as OneRegisterInstruction).registerA)
        assertTrue("registers", setOf(gate.flag, gate.video, gate.shown).all { it < 16 })
        assertEquals("distinct registers", 3, setOf(gate.flag, gate.video, gate.shown).size)
        val read = body.indexOfFirst {
            it.opcode == Opcode.IGET_OBJECT && (it as TwoRegisterInstruction).registerA == gate.shown &&
                it.getReference<FieldReference>()?.type == "Ljava/lang/Integer;"
        }
        val written = (read + 1 until gate.at).filter {
            body[it].opcode.setsRegister() && (body[it] as? OneRegisterInstruction)?.registerA == gate.shown
        }
        assertTrue("nothing overwrites the shown track first: $written", written.isEmpty())
    }

    private companion object {
        const val FINAL_OR_PRIVATE = 0x10 or 0x2
    }
}
