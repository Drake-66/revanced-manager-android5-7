package app.revanced.manager.flutter

import android.app.SearchManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import app.revanced.manager.flutter.utils.Aapt
import app.revanced.manager.flutter.utils.aligning.ZipAligner
import app.revanced.manager.flutter.utils.signing.Signer
import app.revanced.manager.flutter.utils.zip.ZipFile
import app.revanced.manager.flutter.utils.zip.structures.ZipEntry
import app.revanced.patcher.PatchBundleLoader
import app.revanced.patcher.Patcher
import app.revanced.patcher.PatcherOptions
import app.revanced.patcher.extensions.PatchExtensions.compatiblePackages
import app.revanced.patcher.extensions.PatchExtensions.description
import app.revanced.patcher.extensions.PatchExtensions.include
import app.revanced.patcher.extensions.PatchExtensions.options
import app.revanced.patcher.extensions.PatchExtensions.patchName
import app.revanced.patcher.patch.PatchClass
import app.revanced.patcher.patch.PatchResult
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.logging.LogRecord
import java.util.logging.Logger


class MainActivity : FlutterActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var installerChannel: MethodChannel
    private var cancel: Boolean = false
    private var stopResult: MethodChannel.Result? = null

    private lateinit var patches: List<PatchClass>

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val patcherChannel = "app.revanced.manager.flutter/patcher"
        val installerChannel = "app.revanced.manager.flutter/installer"
        val openBrowserChannel = "app.revanced.manager.flutter/browser"

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, openBrowserChannel).setMethodCallHandler { call, result ->
            if (call.method == "openBrowser") {
                val searchQuery = call.argument<String>("query")
                openBrowser(searchQuery)
                result.success(null)
            } else {
                result.notImplemented()
            }
        }

        val mainChannel =
            MethodChannel(flutterEngine.dartExecutor.binaryMessenger, patcherChannel)

        this.installerChannel =
            MethodChannel(flutterEngine.dartExecutor.binaryMessenger, installerChannel)

        mainChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "runPatcher" -> {
                    val originalFilePath = call.argument<String>("originalFilePath")
                    val inputFilePath = call.argument<String>("inputFilePath")
                    val patchedFilePath = call.argument<String>("patchedFilePath")
                    val outFilePath = call.argument<String>("outFilePath")
                    val integrationsPath = call.argument<String>("integrationsPath")
                    val selectedPatches = call.argument<List<String>>("selectedPatches")
                    val options = call.argument<Map<String, Map<String, Any>>>("options")
                    val cacheDirPath = call.argument<String>("cacheDirPath")
                    val keyStoreFilePath = call.argument<String>("keyStoreFilePath")
                    val keystorePassword = call.argument<String>("keystorePassword")

                    if (
                        originalFilePath != null &&
                        inputFilePath != null &&
                        patchedFilePath != null &&
                        outFilePath != null &&
                        integrationsPath != null &&
                        selectedPatches != null &&
                        options != null &&
                        cacheDirPath != null &&
                        keyStoreFilePath != null &&
                        keystorePassword != null
                    ) {
                        cancel = false
                        runPatcher(
                            result,
                            originalFilePath,
                            inputFilePath,
                            patchedFilePath,
                            outFilePath,
                            integrationsPath,
                            selectedPatches,
                            options,
                            cacheDirPath,
                            keyStoreFilePath,
                            keystorePassword
                        )
                    } else result.notImplemented()
                }

                "stopPatcher" -> {
                    cancel = true
                    stopResult = result
                }

                "getPatches" -> {
                    val patchBundleFilePath = call.argument<String>("patchBundleFilePath")!!
                    val cacheDirPath = call.argument<String>("cacheDirPath")!!

                    try {
                        val patchBundleFile = File(patchBundleFilePath)
                        patchBundleFile.setWritable(false)
                        patches = PatchBundleLoader.Dex(
                            patchBundleFile,
                            optimizedDexDirectory = File(cacheDirPath)
                        )
                    } catch (ex: Exception) {
                        return@setMethodCallHandler result.notImplemented()
                    } catch (err: Error) {
                        return@setMethodCallHandler result.notImplemented()
                    }

                    JSONArray().apply {
                        patches.forEach {
                            JSONObject().apply {
                                put("name", it.patchName)
                                put("description", it.description)
                                put("excluded", !it.include)
                                put("compatiblePackages", JSONArray().apply {
                                    it.compatiblePackages?.forEach { compatiblePackage ->
                                        val compatiblePackageJson = JSONObject().apply {
                                            put("name", compatiblePackage.name)
                                            put(
                                                "versions",
                                                JSONArray().apply {
                                                    compatiblePackage.versions.forEach { version ->
                                                        put(version)
                                                    }
                                                })
                                        }
                                        put(compatiblePackageJson)
                                    }
                                })
                                put("options", JSONArray().apply {
                                    it.options?.forEach { option ->
                                        JSONObject().apply {
                                            put("key", option.key)
                                            put("title", option.title)
                                            put("description", option.description)
                                            put("required", option.required)

                                            fun JSONObject.putValue(
                                                value: Any?,
                                                key: String = "value"
                                            ) = if (value is Array<*>) put(
                                                key,
                                                JSONArray().apply {
                                                    value.forEach { put(it) }
                                                })
                                            else put(key, value)

                                            putValue(option.value)

                                            put("values", null)
                                            put("valueType", option::class.simpleName)
                                        }.let(::put)
                                    }
                                })
                            }.let(::put)
                        }
                    }.toString().let(result::success)
                }

                else -> result.notImplemented()
            }
        }
    }

    fun openBrowser(query: String?) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }
    
    @OptIn(InternalCoroutinesApi::class)
    private fun runPatcher(
        result: MethodChannel.Result,
        originalFilePath: String,
        inputFilePath: String,
        patchedFilePath: String,
        outFilePath: String,
        integrationsPath: String,
        selectedPatches: List<String>,
        options: Map<String, Map<String, Any>>,
        cacheDirPath: String,
        keyStoreFilePath: String,
        keystorePassword: String
    ) {
        val originalFile = File(originalFilePath)
        val inputFile = File(inputFilePath)
        val patchedFile = File(patchedFilePath)
        val outFile = File(outFilePath)
        val integrations = File(integrationsPath)
        val keyStoreFile = File(keyStoreFilePath)
        val cacheDir = File(cacheDirPath)

        Thread {
            fun updateProgress(progress: Double, header: String, log: String) {
                handler.post {
                    installerChannel.invokeMethod(
                        "update",
                        mapOf(
                            "progress" to progress,
                            "header" to header,
                            "log" to log
                        )
                    )
                }
            }

            fun postStop() = handler.post { stopResult!!.success(null) }

            // Setup logger
            Logger.getLogger("").apply {
                handlers.forEach {
                    it.close()
                    removeHandler(it)
                }

                object : java.util.logging.Handler() {
                    override fun publish(record: LogRecord) {
                        if (record.loggerName?.startsWith("app.revanced") != true || cancel) return

                        updateProgress(-1.0, "", record.message)
                    }

                    override fun flush() = Unit
                    override fun close() = flush()
                }.let(::addHandler)
            }

            try {
                updateProgress(0.0, "", "Copying APK")

                if (cancel) {
                    postStop()
                    return@Thread
                }

                originalFile.copyTo(inputFile, true)

                if (cancel) {
                    postStop()
                    return@Thread
                }

                updateProgress(0.05, "Reading APK...", "Reading APK")

                val patcher = Patcher(
                    PatcherOptions(
                        inputFile,
                        cacheDir,
                        Aapt.binary(applicationContext).absolutePath,
                        cacheDir.path,
                    )
                )

                if (cancel) {
                    postStop()
                    return@Thread
                }

                updateProgress(0.1, "Loading patches...", "Loading patches")

                val patches = patches.filter { patch ->
                    val isCompatible = patch.compatiblePackages?.any {
                        it.name == patcher.context.packageMetadata.packageName
                    } ?: false

                    val compatibleOrUniversal =
                        isCompatible || patch.compatiblePackages.isNullOrEmpty()

                    compatibleOrUniversal && selectedPatches.any { it == patch.patchName }
                }.onEach { patch ->
                    options[patch.patchName]?.forEach { (key, value) ->
                        patch.options?.set(key, value)
                    }
                }

                if (cancel) {
                    postStop()
                    return@Thread
                }

                updateProgress(0.15, "Executing...", "")

                // Update the progress bar every time a patch is executed from 0.15 to 0.7
                val totalPatchesCount = patches.size
                val progressStep = 0.55 / totalPatchesCount
                var progress = 0.15

                patcher.apply {
                    acceptIntegrations(listOf(integrations))
                    acceptPatches(patches)

                    runBlocking {
                        apply(false).collect(FlowCollector { patchResult: PatchResult ->
                            if (cancel) {
                                handler.post { stopResult!!.success(null) }
                                this.cancel()
                                this@apply.close()
                                return@FlowCollector
                            }

                            val msg = patchResult.exception?.let {
                                val writer = StringWriter()
                                it.printStackTrace(PrintWriter(writer))
                                "${patchResult.patchName} failed: $writer"
                            } ?: run {
                                "${patchResult.patchName} succeeded"
                            }

                            updateProgress(progress, "", msg)
                            progress += progressStep
                        })
                    }
                }

                if (cancel) {
                    postStop()
                    patcher.close()
                    return@Thread
                }

                updateProgress(0.8, "Building...", "")

                val res = patcher.get()
                patcher.close()

                ZipFile(patchedFile).use { file ->
                    res.dexFiles.forEach {
                        if (cancel) {
                            postStop()
                            return@Thread
                        }
                        file.addEntryCompressData(
                            ZipEntry.createWithName(it.name),
                            it.stream.readBytes()
                        )
                    }
                    res.resourceFile?.let {
                        file.copyEntriesFromFileAligned(
                            ZipFile(it),
                            ZipAligner::getEntryAlignment
                        )
                    }
                    file.copyEntriesFromFileAligned(
                        ZipFile(inputFile),
                        ZipAligner::getEntryAlignment
                    )
                }

                if (cancel) {
                    postStop()
                    return@Thread
                }

                updateProgress(0.9, "Signing...", "Signing APK")

                Signer("ReVanced", keystorePassword).signApk(patchedFile, outFile, keyStoreFile)

                updateProgress(1.0, "Patched", "Patched")
            } catch (ex: Throwable) {
                if (!cancel) {
                    val stack = ex.stackTraceToString()
                    updateProgress(
                        -100.0,
                        "Failed",
                        "An error occurred:\n$stack"
                    )
                }
            }

            handler.post { result.success(null) }
        }.start()
    }
}
